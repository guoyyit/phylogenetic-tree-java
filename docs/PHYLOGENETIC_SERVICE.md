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
| `DUPLICATE_NAME` | 两条 FASTA 记录的序列名（去掉头部分隔空白后的首段）相同。属于客户端错误，消息中给出重复的名字 |
| `INSUFFICIENT_INFORMATION` | 长度为 0；或不存在“至少含两个非空缺/非未知碱基”的列；或任意某序列对之间没有任何可比较位点（即使比对整体仍包含有效列） |

### 提交阶段校验与异步执行的边界

上表中的**所有**校验都在 `POST` 请求线程内同步完成，发生在任何任务目录、
输入文件或中间文件创建**之前**：

- 校验失败立即返回 HTTP 400，响应体 `{"error": "<原因>", "message": "<可读提示>"}`，
  **不会**先返回 `202` 再让任务在异步执行中失败，也**不会**创建任务或在工作目录
  留下任务目录/中间文件（有测试断言工作目录为空）。
- 只有全部校验通过后，服务才分配任务 ID、原子写入 `input.fasta`/`config.properties`
  并返回 `202 {"id": ...}`。
- “任意一对序列之间无可比较位点”即使在比对整体仍可形成有效比对（例如
  `a=AC--, b=--GT, c=ACGT`）时，也在提交阶段以 `INSUFFICIENT_INFORMATION` 拒绝；
  该原因与 `DUPLICATE_NAME` 等其他原因在 `error` 字段中可明确区分，消息中给出
  具体的序列对名字。
- 自举**列重采样**后的中间比对可能偶然出现某对序列无可比较位点（原始输入合法）。
  该情形不是拒绝条件：重采样距离计算对这样的序列对取距离 0，保证单次试验结果
  仍然只由 `(seed, 试验索引)` 决定，不依赖异常路径或线程调度。

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
3. 一对序列没有任何可比较位点时，在**提交阶段**以
   `INSUFFICIENT_INFORMATION` 同步拒绝（HTTP 400，消息给出该序列对）。
   注意该规则针对原始输入逐对检查；自举重采样内部的偶然退化按第 4 节处理。

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
- 参考拓扑是原比对的 NJ 树；对其每个**非平凡内部分支**（分裂两侧都至少有
  2 条叶序列），统计在自举树中诱导出**相同叶集分裂（split）**的试验比例，
  写入该内部节点的支持度（根节点为平凡分裂，支持度记为 1；与根相邻的两个
  垂支只有单侧叶集，不属于内部分支，支持度记为 0）。分裂以叶集合的规范化
  位集合表示（固定取“不含索引 0 叶序列”的一侧），与根的取向无关。
  分裂表示没有固定宽度上限，**不再限制序列数量**：已验证不少于 128 条序列、
  比对长度不少于 200 的输入在默认配置（100 次试验）下可以完成，并为全部
  内部节点输出支持度；62、63、64、65 条序列的边界规模都会得到确定、完整、
  无越界的支持度结果。
- 试验 r 的随机源为 `Random(seed ^ mix(r))`（splitmix64 风格混合），
  即试验 r 的结果**只依赖 (seed, r)**，与线程调度、完成顺序、并发度无关。
  因此相同输入 + 相同种子下，全部自举树与一致性树**逐字节一致**，
  并发度为 1 或不小于 8 结果相同（大规模 128×200 输入有测试覆盖）。

## 5. 并发、取消、超时

- 每个任务内部用固定大小线程池并发跑试验，`concurrency` 可配置（每任务、
  每请求）；多个任务在独立的调度线程上并存，互不影响。
- 取消（`POST /api/analyses/{id}/cancel`）与超时（`timeoutSeconds`，
  0 表示不限）会在两次提交之间被即时感知，停止派发新试验并中断在途试验。
  大规模运行（如 128×200、千万级试验的压力场景）下行为相同。
- **不会留下部分写入的最终结果**：每个试验树先写临时文件再原子 rename；
  取消/超时只可能留下若干“完整的单试验文件”，`consensus.nwk` 与
  `bootstrap-trees.nwk` 只在全部试验成功后一次性原子写出，因此取消/超时后
  这两个文件必然不存在，且不会残留 `.tmp-*` 文件（有大规模取消/超时测试
  断言）。一个任务的取消或超时不会影响同进程其他任务的继续执行与输出
  （有并发任务隔离测试覆盖）。

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
组装。恢复流程不会重写任何已存在的试验文件（大规模 128×200、数千次试验的
“崩溃恢复”测试会校验已完成试验文件的字节内容与修改时间在恢复后保持不变）。
因为单试验结果只由 (seed, 索引) 决定，**恢复后的最终结果与不间断运行
逐字节一致**（`bootstrap-trees.nwk` 与 `consensus.nwk` 均字节比较）。
恢复前会清理残留的 `.tmp-*` 临时文件。

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
phylo.default-concurrency=2                    # 服务层默认并发度
```

请求级配置（JSON 字段或 multipart 表单参数）：`replicates`（默认 100）、
`seed`（默认 0）、`concurrency`（REST 请求未提供时默认 1；直接使用
`AnalysisService` 且值 <= 0 时回落到 `phylo.default-concurrency`）、
`timeoutSeconds`（默认 0，即不限）。并发度只影响执行速度，不影响结果字节。

REST 一览：

- `POST /api/analyses`：JSON 提交，校验通过返回 `202 {"id": ...}`，
  校验失败同步返回 400 与可区分原因
- `POST /api/analyses/upload`：multipart 上传 FASTA，校验语义相同
- `GET  /api/analyses/{id}`：状态与已完成试验数
- `POST /api/analyses/{id}/cancel`：取消
- `GET  /api/analyses/{id}/output/consensus.nwk`
- `GET  /api/analyses/{id}/output/bootstrap-trees.nwk`

## 9. 验证方法

自动化测试（`mvn test`）覆盖本文件声明的全部边界，关键场景：

| 场景 | 测试 |
| --- | --- |
| 提交阶段拒绝：某序列对无可比较位点（含“整体仍有效”情形） | `SubmissionValidationTest`、`PhyloControllerIntegrationTest`（断言 400 而非 202，且工作目录无任务目录） |
| 提交阶段拒绝：重复序列名，原因与其他拒绝可区分 | 同上（断言 `DUPLICATE_NAME` 400，非 500） |
| 128 序列 × 200 列 × 默认 100 试验完成；全部内部节点支持度存在、在 [0,1] 内、且与独立重算的分裂频率逐一相等；支持度为 1/100 整数倍 | `LargeScaleConsensusTest#completesAt128Taxa200ColumnsWithDefaultReplicatesAndAllSupports` |
| 62/63/64/65 序列边界：确定结果、支持度不缺失/不错位/不越界、跨并发度逐字节一致 | `LargeScaleConsensusTest#boundaryTaxonCountsGiveDeterminedCompleteSupports` |
| 相同输入 + 相同种子，并发度 1 与 8 逐字节一致（128×200） | `LargeScaleExecutionTest#sameSeedIsByteIdenticalAtConcurrencyOneAndEight` |
| 大规模中断恢复后与不间断运行逐字节一致；已完成试验不重算（字节与修改时间不变）、不重复计入 | `LargeScaleExecutionTest#resumedLargeRunIsByteIdenticalAndSkipsDoneReplicates` |
| 大规模取消/超时无最终输出、无临时残留，且不影响其他任务 | `LargeScaleExecutionTest#cancelledLargeRunWritesNoFinalOutputAndDoesNotAffectOthers`、`#timedOutLargeRunWritesNoFinalOutput` |
| 既有行为不变：可加性距离重构、全零退化、空输入、长度不一致、非法字符、序列不足、Newick 无损往返 | `NeighborJoiningTest`、`FastaReaderTest`、`DistanceCalculatorTest`、`NewickRoundTripTest`、`AnalysisServiceTest` |

分裂支持度的正确性校验不依赖被测实现自身：测试直接按叶名集合独立重算每个
参考树分裂在全部试验树中的出现频率，与输出中的标注值逐一比较。
