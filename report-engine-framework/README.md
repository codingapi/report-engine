# report-engine-framework

报表核心框架：**声明式数据模型 + 内存渲染引擎**。纯 Java 实现，不依赖 Spring，可单独发布到 Maven Central。

模板层（Excel/Univer 的视觉呈现）与语义层（数据绑定与计算）完全分离——模板负责"长什么样"，本框架负责"取什么数、怎么算、怎么填"。

## 包结构（按业务域划分）

```
com.codingapi.report
├── data                         数据域：数据从哪来
│   ├── datamodel    DataModel — 可复用语义层（datasources + datasets + relationships）
│   ├── datasource   DataSource / DataSourceType / RawTable
│   │   ├── DataExtractor        提取器 SPI（按 DataSourceType 取数 → RawTable）
│   │   └── csv/CsvDataExtractor CSV 实现
│   ├── dataset      Dataset(sealed) → TableDataset / UnionDataset
│   │                Field / FieldRef / DataType / Query / UnionMember
│   └── relation     Relationship / RelationOrigin / JoinType（跨数据集，独立成域）
│
├── operator                     算子域：作用在 RawTable 行上的运算
│   ├── Values       共享数值强转/比较（toDouble / equals / compare）
│   ├── aggregation  Aggregation + Aggregator(SPI) + 各实现 + Aggregators 注册表
│   └── condition    Condition / CompareOperator + ConditionPredicate(SPI) + 各实现 + 注册表
│
├── param                        参数域：运行时值解析
│   └── Parameter / ParamSource(sealed) / ValueRef(sealed) / ParamContext
│
└── render                       渲染域：数据如何映射到单元格
    ├── Report       报表定义（dataModelId + cellBindings + loopBlocks + summaries + parameters）
    ├── grid         CellBinding(sealed) → TextCell / FieldCell
    │                CellRef / Expansion / ExpandMode / LoopBlock / SummaryRow / SummaryCell
    └── engine       ReportRenderer（核心引擎）/ Operators（filter + join）
```

**划分原则**：按真实业务域组织，父包要名副其实。`data` 聚合数据定义，`relation` 跨数据集所以独立而非塞进 `dataset`；`operator` 是"聚合算子 + 条件算子"的共享抽象。

## 两层分离

| 层 | 承载 | 归属 |
|---|---|---|
| **模板层** | 静态文本、样式、边框、合并、行列尺寸 | Univer/Excel Workbook（`report-engine-excel`） |
| **语义层（本框架）** | 取数、扩展、分组、聚合、条件、循环、参数 | `Report` 的 cellBindings / loopBlocks / summaries |

渲染时 `ReportRenderer` 以模板为画布，把语义层"刷"上去——纯静态格子（如表头）只存在于模板，无需进入本模型。

## 扩展点（统一用 `supports()` + 注册表范式）

| 扩展什么 | 怎么扩展 |
|---|---|
| **新数据源类型**（DB/API/…） | 实现 `DataExtractor`（`supports(type)` + `extract()`），注册到 `ReportRenderer` 的 extractors 列表 |
| **新比较算子**（LIKE/IN/BETWEEN…） | 实现 `ConditionPredicate`，在 `ConditionPredicates` 注册表登记一行 |
| **新聚合方式**（COUNT_TRUE…） | 实现 `Aggregator`，在 `Aggregators` 注册表登记一行 |

三者同一套范式，新增能力无需改动 `Operators` 或任何调用方。未注册的算子/聚合会**显式抛异常**，不会静默放行。

## 密封类型（编译期穷尽，switch/instanceof 全覆盖）

- `Dataset` → `TableDataset`（物理表）/ `UnionDataset`（UNION 派生）
- `CellBinding` → `TextCell`（文本插值）/ `FieldCell`（字段绑定）
- `ParamSource` → `External` / `Cell` / `Constant`
- `ValueRef` → `Literal` / `Param` / `LoopField`

## 渲染流水线

```
render(dataModel, report, paramContext, templateWorkbook)
  1. seedTemplate    加载模板单元格/样式到画布
  2. renderFree      非循环区：提取数据集 → greedy-join → 按条件过滤 → 纵向带展开（分组/列表/聚合 + 小计/总计）→ 文本插值
  3. renderLoop      循环块：提取驱动数据集 → 逐行迭代更新 ParamContext → 渲染块内格子（支持子查询）
  4. buildWorkbook   画布 → Workbook 输出
```

**关键设计**：所有计算在 Java 内存完成（不下推 SQL），因此支持跨数据源 JOIN（如 MySQL 表 JOIN CSV 文件）。

## 测试

纯内存测试，无外部依赖（数据源用 `src/test/resources/data/` 下的 CSV），覆盖 7 种报表场景：简单列表、合并列表、多级统计、工资条循环、主从合并、小计合计、跨源 UNION。

```bash
./mvnw test -pl report-engine-framework
```
