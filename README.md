# 本地系统发育分析服务 (phylogenetic-tree-java)

输入已比对的同源核酸序列（FASTA 文件或进程内/HTTP 提交），输出反映亲缘关系的二叉树：
邻接（Neighbor-Joining）建树、位点带放回自举支持度、多数规则一致性树。
所有计算与存储均在本地完成，**不依赖任何外部服务**。

## 构建与运行

```bash
mvn -o test          # 运行全部测试（离线仓库）
mvn spring-boot:run  # 启动本地 HTTP 服务，默认端口 8080
```

可配置项（`application.properties` 或系统属性）：

- `phylo.work-dir`：检查点与结果的本地目录，默认 `${java.io.tmpdir}/phylo-work`
- `phylo.concurrency`：默认并发度，默认 `4`

## 分析管线

1. **校验**：等长、至少两条序列、名称非空且唯一、字符合法、信息量充足。
2. **两两距离**：p-distance，成对比对缺失删除（pairwise deletion）。
3. **建树**：确定性的邻接法，支长非负（浮点负支夹零），最后剩三个节点时确定生根。
4. **自举**：位点带放回重采样，重复次数与随机种子可配。
5. **一致性树**：严格多数规则（出现比例严格大于 0.5），内节点标注支持比例（4 位小数）。
6. **输出**：规范 Newick；`consensus.nwk` 与每个重复一棵树的 `bootstrap-trees.nwk`。

## 空缺与模糊碱基处理规则

校验阶段接受大小写，`U` 一律归一化为 `T`。

| 字符 | 含义 | 距离计算规则 |
| --- | --- | --- |
| `A C G T` | 确定碱基 | 正常比较 |
| `- . ~` | 比对空缺（gap） | 该位点对该序列对**排除** |
| `?` | 未知/缺失（missing） | 同空缺，**排除** |
| `R Y S W K M` | 2 碱基 IUPAC 简并码 | 展开为可能碱基集合 |
| `B D H V` | 3 碱基简并码 | 展开为可能碱基集合 |
| `N X` | 任意碱基（4 种全可能） | 与任何非空缺都相交，因此总是匹配 |

对每个序列对，逐位点：

- 任一侧为空缺/缺失（`- . ~ ?`）时，该位点不计入分母也不计入错配；
- 否则取两个简并码的可能碱基集合：**集合相交记为匹配，完全不相交记为错配**；
- 距离 = 错配位点数 / 双方均可比的位点数。

因此距离天然满足对称性、非负性；相同序列距离为 0。
若任意一对序列没有**任何**可比位点，输入以 `INSUFFICIENT_INFORMATION` 拒绝。

## 明确拒绝的输入（不会静默产出）

拒绝时抛出 `InputValidationException`，HTTP 映射为 400 并携带可区分的 `reason`：

| reason | 触发条件 |
| --- | --- |
| `EMPTY_INPUT` | 空提交或没有序列 |
| `TOO_FEW_SEQUENCES` | 序列数少于 2 |
| `LENGTH_MISMATCH` | 等长比对中出现长度不一致（含空序列） |
| `INVALID_CHARACTER` | 出现非 IUPAC/非空缺字符（错误信息含序列名与位点） |
| `INVALID_NAME` | 序列名为空 |
| `DUPLICATE_NAME` | 序列名重复 |
| `INSUFFICIENT_INFORMATION` | 某对序列没有任何双方均可比的位点 |

## 随机性与并发语义

- 每个自举重复使用**独立的确定性随机流**：
  `Random(splitmix64(seed, replicateIndex))`，与重复下标一一绑定。
  因此相同输入、相同种子、相同重复次数下，输出**逐字节一致**，
  与并发度、线程调度与任务完成顺序无关（有测试覆盖并发度 1 与 8 的一致性）。
- 不同种子通常产生不同重采样与树。
- 全部距离为 0 的退化输入也产出确定、有效、叶间路径长度全为 0 的树。
- 邻接法平局固定选择矩阵下标最小的一对（`q` 最小，再按 `(i,j)`）。

## 并发、取消与超时

- `TaskManager` 用独立线程池并发执行多个分析任务，任务之间完全隔离
  （各自的检查点目录、取消令牌与线程池），取消/超时一个任务不影响其他任务。
- 自举重复在线程池内并发，并发度可在提交时按任务配置。
- 取消与超时为协作式：停止提交未开始的重复并尽快收敛。
  最终结果文件（`consensus.nwk`、`bootstrap-trees.nwk`）只在全部完成后
  通过临时文件 + 原子 rename 落盘，因此取消/超时/崩溃都**不会留下部分最终结果**。

## 中断恢复

任务 ID 是对“序列内容 + 重复次数 + 种子 + 并发度”的 SHA-256 摘要（前 16 位）。
每个完成的重复立即原子写入 `<work-dir>/<taskId>/replicates/replicate-00000.nwk`。
进程重启后用相同输入与配置再次提交：

- 任务 ID 相同，自动加载已完成的重复，**不重复计算、不重复计入**；
- 仅补齐缺失重复，最终一致性树与全部自举树和不间断运行**逐字节一致**
  （见 `CrashRecoveryTest`、`TaskManagerRestartTest`）。

## Newick 格式

- 规范形式：叶节点为序列名，内节点标签为支持度（如 `0.9700`），
  根节点无支持标签；支长使用最短的十进制表示（内部保留 10 位小数精度），
  支长为 0 时省略 `:0`。
- 服务可无损解析自己产出的 Newick：`parse(write(t))` 再序列化与原输出逐字节一致。
- 多棵树为每行一个以 `;` 结尾的 Newick。

## HTTP 接口（仅本地）

```bash
# 直接提交序列
curl -X POST localhost:8080/api/analyses \
  -H 'Content-Type: application/json' \
  -d '{"sequences":[{"name":"a","sequence":"ACGT"},
                    {"name":"b","sequence":"ACGA"}],
       "replicates":100,"seed":42,"concurrency":4,"timeoutMillis":0}'

# 上传本地 FASTA 文件
curl -X POST localhost:8080/api/analyses/fasta \
  -F 'file=@alignment.fasta' -F 'replicates=100' -F 'seed=42'

# 查询状态 / 结果 / 取消
curl localhost:8080/api/analyses/<taskId>
curl localhost:8080/api/analyses/<taskId>/result
curl -X POST localhost:8080/api/analyses/<taskId>/cancel
```

`timeoutMillis <= 0` 表示不限制超时。

## 测试覆盖

- 可加性距离矩阵的精确重构（叶间路径长度误差 ≤ 1e-9）
- 全零距离退化输入的确定性与非负支长
- 空缺/缺失/模糊碱基的距离规则、对称性、非负性、相同序列为 0
- 七类输入拒绝原因可区分
- 同种子逐字节可复现，且与并发度无关
- 多数规则支持度标注与 Newick 无损往返
- 崩溃恢复不重复计算且结果一致
- 多任务并发隔离、取消与超时不留部分结果
