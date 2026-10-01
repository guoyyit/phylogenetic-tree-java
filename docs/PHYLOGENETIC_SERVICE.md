# 本地系统发育分析服务

输入已比对的同源序列（FASTA），输出反映亲缘关系的二叉树（邻接法，
Neighbor-Joining）以及每条分支的自举支持度（bootstrap support）。
所有数据只来自进程内提交或本地工作目录，**不访问任何外部服务**。

## 1. 输入与拒绝原因

`POST /api/analyses`（JSON 体 `{ "fasta": "...", ... }`）或
`POST /api/analyses/upload`（multipart 文件）提交任务。输入必须是等长比对。
校验失败时 HTTP 400，`error` 字段为以下**可区分**的机器可读原因之一，
服务不会静默产出结果：

| 原因 | 触发条件 |
| --- | --- |
| `EMPTY_INPUT` | 空串/纯空白；没有任何 FASTA 记录；只有头没有序列数据 |
| `TOO_FEW_SEQUENCES` | 非空序列少于 2 条 |
| `LENGTH_MISMATCH` | 序列长度不一致（含某条为空） |
| `INVALID_CHARACTER` | 出现合法字母表之外的字符 |
| `INSUFFICIENT_INFORMATION` | 长度为 0；或不存在“至少含两个非空缺/非未知碱基”的列；或某序列对之间没有任何可比较位点 |

接受的字符（大小写不敏感，小写自动归一为大写）：

- 明确碱基：`A C G T`
- IUPAC 模糊碱基：`R Y S W K M B D H V`
- 完全未知：`N X ?`
- 空缺（gap）：`-` `.`

## 2. 成对距离规则（文档化、确定）

距离为**成对删除（pairwise deletion）的 p 距离**：
`distance(i,j) = 错配位点数 / 可比较位点数`。

逐位点规则（对序列对中的每条序列独立判断）：

1. 任一方该位点为空缺（`-`、`.`）或完全未知（`N`、`X`、`?`）时，该位点
   **不参与**这一对的比较（不计入分母，也不计入分子）。
2. 否则把字符解释为核苷酸集合：明确碱基为单元素集合，模糊碱基为 IUPAC
   集合（如 `R={A,G}`，`Y={C,T}`，`B={C,G,T}` 等）。两个集合**有交集**
   记为匹配，**交集为空**记为错配。例如 `R` 对 `A` 匹配，`R` 对 `Y` 错配。
3. 一对序列没有任何可比较位点时，以 `INSUFFICIENT_INFORMATION` 拒绝。

由此距离矩阵天然满足：对称性 `d(i,j)=d(j,i)`、非负性 `d(i,j)>=0`、
对角线为 0；相同序列距离为 0。

## 3. 建树（邻接法）

`NeighborJoining` 从距离矩阵构建二叉树：

- 每个分支长度 `>= 0`。标准 NJ 公式在数值扰动下可能产生绝对值小于
   `1e-9` 的负值，这些被钳为 0；其余非负结果按公式保留。
- 对**可加（additive）**距离矩阵，任意两条序列在树上的路径长度重现其
  成对距离，误差不超过约定容差 `1e-6`（单元测试精确验证）。
- 并列最小 Q 值按索引顺序确定性打破平局。
- **退化输入**：全部距离为 0（例如所有序列完全相同）也产出确定、有效、
  枝长全为 0 的二叉树；2 条序列时产出二分叉根树。

## 4. 自举重采样与支持度

- 每次试验对比对的**列（位点）做有放回抽样**，列数与原比对相同，再重算
  距离并建树。
- 参考拓扑是原比对的 NJ 树；对其每个内部分支，统计在自举树中诱导出
  **相同叶集分裂（split）**的试验比例，写入该内部节点的支持度
  （根节点为平凡分裂，支持度记为 1）。分裂以叶集合的规范位掩码表示，
  与根的取向无关。最多支持 63 条序列。
- 试验 r 的随机源为 `Random(seed ^ mix(r))`（splitmix64 风格混合），
  即试验 r 的结果**只依赖 (seed, r)**，与线程调度、完成顺序、并发度无关。
  因此相同输入 + 相同种子下，全部自举树与一致性树**逐字节一致**，
  并发度为 1 或 N 结果相同（有测试覆盖）。

## 5. 并发、取消、超时

- 每个任务内部用固定大小线程池并发跑试验，`concurrency` 可配置（每任务、
  每请求）；多个任务在独立的调度线程上并存，互不影响。
- 取消（`POST /api/analyses/{id}/cancel`）与超时（`timeoutSeconds`，
  0 表示不限）会在两次提交之间被即时感知，停止派发新试验并中断在途试验。
- **不会留下部分写入的最终结果**：每个试验树先写临时文件再原子 rename；
  取消/超时只可能留下若干“完整的单试验文件”，`consensus.nwk` 与
  `bootstrap-trees.nwk` 只在全部试验成功后一次性原子写出。

## 6. 中断恢复（崩溃重启）

工作目录布局：

```
<work-dir>/<task-id>/
  input.fasta              # 原子写入的原始输入
  config.properties        # replicates/seed/concurrency/timeoutSeconds
  status.json              # 最近状态
  replicates/rep-000000.nwk, rep-000001.nwk, ...   # 原子写入的单试验树
  consensus.nwk            # 仅完成时原子出现
  bootstrap-trees.nwk      # 仅完成时原子出现（每试验一行）
```

应用启动时自动扫描工作目录：已有最终输出的任务跳过；未完成任务的已存在
试验文件**不会重复计算、也不会重复计入**，只补齐缺失试验，然后按索引顺序
组装。因为单试验结果只由 (seed, 索引) 决定，**恢复后的最终结果与不间断
运行逐字节一致**（有“崩溃恢复”测试覆盖）。恢复前会清理残留的
`.tmp-*` 临时文件。

## 7. 输出格式与无损往返

树以 Newick 文本写出：

- 分支长度固定 8 位小数（0 规范为 `0`）；
- 支持度写在内部节点标签后的 `[` 注释中，4 位小数（0/1 规范为 `0`/`1`）；
- 含特殊字符的名字用单引号引用（内部 `'` 双写）。

`NewickParser` 能无损解析本服务写出的内容，重新序列化后与原输出**完全
一致**（字节级，测试覆盖）。`bootstrap-trees.nwk` 每行一棵树。

## 8. 配置

`application.properties`：

```
phylo.work-dir=${java.io.tmpdir}/phylo-tasks   # 本地任务工作目录
phylo.default-concurrency=2                    # 默认并发度
```

REST 一览：

- `POST /api/analyses`：JSON 提交，返回 `202 {"id": ...}`
- `POST /api/analyses/upload`：multipart 上传 FASTA
- `GET  /api/analyses/{id}`：状态与已完成试验数
- `POST /api/analyses/{id}/cancel`：取消
- `GET  /api/analyses/{id}/output/consensus.nwk`
- `GET  /api/analyses/{id}/output/bootstrap-trees.nwk`
