# 报表模型设计说明

> 模型源码：`src/main/java/com/example/report/model/`
> 验证测试：`ReportModelTest`（8 个用例，同时充当可执行文档）

本模型用于设计报表引擎的**核心能力**，先把概念建清楚，再考虑系统交互如何落地。

---

## 一、为什么这样分层

设计起点是厘清一个被混用的词——"数据源"。它实际承担了三件不同的事，拆开后所有纠结都消失：

| 容易混的说法 | 实际是什么 | 模型里的类 |
|---|---|---|
| "一个数据源叫学生数据" | 一张**可查询的表/数据集** | `Dataset` |
| "一个数据源下有几个表" | 一个**连接**（库/API） | `DataSource` |
| "三个数据源之间存在关系" | **跨数据集的关联** | `Relationship` |

`DataSourceType` 按**提取器种类**划分（`DB`/`API`/`EXCEL`/`JSON`，后续可加 ES/MONGO），
**不区分数据库厂商**——计算全在 Java、关系库一律走 JDBC，MySQL/Postgres 语法无差异，
厂商（驱动/URL）只是连接配置。

关键结论：

- **报表不绑定连接，绑定 `Dataset`（单表/单查询）。** 库里 50 张表只用 3 张，就只建 3 个
  `Dataset`。这化解了"数据源整体 vs 单表使用"的两难：连接保持整体，使用粒度是单表。
- **关系挂在数据模型上，不属于任何单个数据源。** 因此能连接来自不同连接的数据集
  （人事库的"员工" ↔ 薪资库的"薪资"）。
- **关系永远非强制。** 不设关系时，多个字段就是各自独立扩展的并列列表。

---

## 二、两个聚合根：DataModel（复用） + Report（具体报表）

关系/数据集应该**建一次、多张报表复用**，所以语义层和报表层拆成两个工件
（对应 Power BI 的 Model/Report、Tableau 的 Data Source/Worksheet、帆软的服务器数据集）：

```
DataModel（可复用语义层，维护一次）          Report A ─┐
├── datasources  连接（只负责提取）           Report B ─┼─ 都引用同一个 DataModel
├── datasets     选出的单表/单查询            Report C ─┘   关系不必重复维护
└── relationships 跨数据集关联（可跨源）

Report（报表层，引用一个 DataModel）
├── dataModelId        引用的数据模型
├── extraRelationships 可选：报表私有关系（例外，不污染共享模型）
├── parameters         报表参数（External 即运行时输入契约）
├── cellBindings       格子绑定（字段 + 行/列扩展 + 分组/合并 + 父格 + 条件）
└── loopBlocks         循环块（带迭代上下文的扩展容器，如薪资条）
```

---

## 三、计算全在 Java：源只负责提取（执行层已落地最小切片）

数据计算（join/过滤/聚合）**全部在 Java 内存完成**，连接只负责"提取规整表"。
这样才能跨库、跨源类型（DB + Excel + CSV…）联合处理。

```
DataSource ──提取──► RawTable(类型归一) ──Java算子(join/filter/aggregate)──► ReportRenderer ──► Excel Workbook
```

执行层在 `src/main/java/com/example/report/engine/`：

| 类 | 职责 |
|---|---|
| `DataExtractor` / `CsvDataExtractor` | 提取器接口 + CSV 实现（按类型扩展 DB/Excel/JSON…） |
| `RawTable` | 类型归一的内存表，列名用限定名 `datasetId.field` |
| `Operators` | 纯 Java 算子：`filter` / `join`(hash, INNER) / `aggregate` |
| `ParamContext` | 渲染时求解参数（External/Literal） |
| `ReportRenderer` | 提取→join→过滤→铺到单元格，产出 Excel `Workbook` |

这印证了前面的设计：`Dataset` 不内嵌 JOIN、关系放模型层——因为 **JOIN 就是
`Operators.join`**，`Relationship` 是喂给它的 JoinSpec。

> 当前是**最小切片**：覆盖 参数化文本 + 纵向 LIST 列表 + 聚合格。父格链/交叉表/循环块的
> 完整展开、单源过滤下推、提取缓存等见第七节。全链路验证见 `engine/FullChainTest`。

---

## 四、展现层：模板/覆盖分层，行关系 / 列关系 / 多级分组

**先分清两层**（这决定了模型怎么接住 Univer 的数据结构）：

- **模板层** = Univer/Excel 工作簿（`report-engine-excel` 的 `Workbook→Sheet→Cell`）。
  承载**全部静态文本、富文本、样式、边框、填充、合并、行列尺寸**。纯标题文字就是普通
  Cell 的值 + 样式，**不进本模型**。
- **覆盖层** = 本模型的 `cellBindings` / `loopBlocks`。只对"有动态行为"的格子按坐标附加语义，
  最终序列化进模板的 `Cell.props` / `Sheet.loopBlocks`（Univer 结构已预留这两个槽位）。

覆盖层的格子是**密封类型 `CellBinding`**，两支：

- `TextCell`：文本，可含参数占位 `${year}`（如标题"${year}年度报表"）——纯文字但带动态参数。
  没有参数的纯静态标题不需要它，直接留模板里。
- `FieldCell`：数据字段绑定，携带下面的扩展属性。

类 Excel 报表里**没有刚性的"表"对象**，结构由 `FieldCell` 的属性涌现：

- **扩展方向** `Expansion`：`VERTICAL` 纵向=行关系 / `HORIZONTAL` 横向=列关系 / `NONE` 不扩展
- **扩展模式** `ExpandMode`：`GROUP` 去重分组 / `LIST` 明细全行
- **合并** `mergeRepeated`：相邻相同值是否合并成跨行单元格
- **父格** `parentCell`：对齐/嵌套的参照格，**串成父格链**做多级分组

行 + 列正交 = **交叉表**。父格链 + GROUP + 合并 = **多级分组统计表**（单位 → 部门 → 明细）：

| 列 | 字段 | expansion | expandMode | merge | parentCell |
|---|---|---|---|---|---|
| 单位 | 单位 | VERTICAL | GROUP | ✅ | 无 |
| 部门 | 部门 | VERTICAL | GROUP | ✅ | 单位格 |
| 明细 | 数据 | VERTICAL | LIST | ❌ | 部门格 |

---

## 五、参数系统：显式参数 + 循环字段

- **报表参数** `Parameter`（来源 `ParamSource`）：`External` 运行时传入（部门报表 deptId，
  = 输入契约）/ `Cell` 单元格联动 / `Constant` 常量。条件右值用 `ValueRef.Param` 引用；
  文本格 `TextCell` 用 `${name}` 占位引用同一套参数（如标题"${year}年度报表"）。
- **循环字段** `ValueRef.LoopField(loopId, field)`：循环当前迭代行的字段，**免预先登记**。
  可发现性来自循环驱动数据集的字段——块内格子能引用的取值来源 =
  报表参数 ∪ 各祖先循环驱动数据集的字段。

**循环驱动是 `Query` 而非裸数据源**：`Query{ datasetId + filters + groupBy }`。
过滤让循环范围可被参数控制（只循环某部门/在职员工），分组决定"逐行迭代"还是"按分组去重迭代"。

**关联两个表有两种表达：** 静态 JOIN（`Relationship`，一次查询合并）；
参数化子查询（循环驱动 + `LoopField`，父迭代传键逐次查，适合异构子源，代价 N+1 需批量预取）。

---

## 六、测试场景一览（`ReportModelTest`）

| 用例 | 验证的设计点 |
|---|---|
| DataModel 复用 | 两张报表引用同一数据模型，关系只维护一次 |
| 跨源关系 | 员工/薪资来自不同连接，关系建在 DataModel 上 |
| 部门报表 | 显式参数 deptId 是输入契约，条件用 :deptId 引用 |
| 薪资条·驱动 Query | 循环驱动是 Query（带过滤），逐人迭代 |
| 薪资条·LoopField | 块内用 LoopField 引用循环行，免登记任何参数 |
| 循环字段发现 | 块内可引用 = 报表参数 ∪ 驱动数据集字段；块外看不到 |
| 交叉表 | 纵向(行)×横向(列)×交叉格聚合 |
| 参数化标题 | 标题是 TextCell，含 ${year} 占位且已声明为参数 |
| 多级分组 | 单位→部门→明细 父格链 + GROUP + 合并 |

## 七、全链路测试（`engine/` 包）

执行层（`com.example.report.engine`）的测试都走"配置 → 计算 → **导出本地 xlsx** → 回读断言"
闭环，文件落到 `target/reports/*.xlsx`。

| 测试类 / 用例 | 验证的能力 | 输出文件 |
|---|---|---|
| `FullChainTest` | 跨两份 CSV join + 参数过滤 + 平均分聚合 + 参数标题 | — |
| `ReportScenarioTest#simpleList` | 简单列表（标题/表头 + 逐行） | simple-list.xlsx |
| `ReportScenarioTest#mergedList` | 带合并列表（分类 GROUP 跨行合并） | merged-list.xlsx |
| `ReportScenarioTest#statistics` | 统计列表（单位/部门分组 + 人数 COUNT + 单位合并） | statistics.xlsx |
| `ReportScenarioTest#masterDetailMergedList` | 主从关联+合并（员工 join 学历 1:N，主表列跨多条学历合并） | master-detail-merge.xlsx |
| `ReportScenarioTest#payslipLoop` | 循环块横向薪资条（每人 标题/表头/数据 三行，跨源按 LoopField 查） | payslip.xlsx |
| `StyleAdaptationTest` | 样式适配：模板的合并标题/富文本/边框，渲染填值后保留；扩展行继承声明格样式 | styled.xlsx |

执行层关键类：`DataExtractor`/`CsvDataExtractor`（提取）、`RawTable`（内存表）、
`Operators`（filter/join/aggregate）、`ParamContext`（参数/循环字段求解）、
`ReportRenderer`（提取→join→过滤→分组/合并/循环→**以模板为底填值保留样式**→Excel `Workbook`）。

运行：

```bash
# 首次需先安装依赖模块（跳过需要 DB 的测试）
./mvnw install -DskipTests
# 运行模型测试 + 执行层全链路测试（纯 POJO/CSV，不需要数据库）
./mvnw test -pl report-engine-example -Dtest='ReportModelTest,FullChainTest,ReportScenarioTest,StyleAdaptationTest'
```

---

## 八、尚未涉及（后续）

- **渲染器完整化**：交叉表横向展开、循环块嵌套、N+1 批量预取
- **更多提取器**：DB(JDBC) / Excel / JSON / ES / MONGO
- **计算字段**：表达式支持（如 `total = base + bonus`，当前由 CSV 预先算好）
- 工程优化：单源过滤下推、提取缓存、聚合前置、内存护栏
- 交互设计：扩展方向/父格怎么在界面上设、参数面板怎么暴露给最终用户
- 模型与前端 JSON 契约对齐
