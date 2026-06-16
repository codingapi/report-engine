# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Behavioral Guidelines

**Bias toward caution over speed.** These guidelines reduce common LLM coding mistakes.

1. **Think Before Coding** — State assumptions explicitly. Surface tradeoffs. Ask before picking silently.
2. **Simplicity First** — Minimum code that solves the problem. No speculative features or abstractions for single-use code.
3. **Surgical Changes** — Touch only what you must. Don't "improve" adjacent code. Remove only orphans YOUR changes created.
4. **Goal-Driven Execution** — Define success criteria. Transform tasks into verifiable goals. Loop until verified.
5. **代码提交纪律** — 完成修改后不要立即 git commit/push，等用户确认后再提交。先展示变更摘要。

## Project Overview

Report Engine — 报表引擎框架，支持通过电子表格界面设计报表模板，配置数据源/条件/计算，生成数据报表。前后端一体化仓库，早期开发阶段 (v0.0.1)。

## Build Commands

### Backend (Java 17 + Maven)

```bash
# 编译全部模块
./mvnw clean compile

# 运行 framework 模块测试（纯内存，无外部依赖）
./mvnw test -pl report-engine-framework

# 运行单个测试类
./mvnw test -pl report-engine-framework -Dtest=ReportScenarioTest

# 运行单个测试方法
./mvnw test -pl report-engine-framework -Dtest=ReportScenarioTest#independentBands

# 运行 excel 模块测试（纯 POI 测试）
./mvnw test -pl report-engine-excel

# 启动 example 应用（端口 8090）
./mvnw spring-boot:run -pl report-engine-example

# 发布到 Maven Central（需要 GPG 签名）
./mvnw clean deploy -P ossrh
```

### Frontend (pnpm monorepo)

```bash
cd report-frontend
pnpm install
pnpm build              # 构建库包（report-univer → report-engine）
pnpm dev:app-pc         # 启动演示应用
pnpm watch:report-univer  # 开发模式 watch
```

## Architecture

### Module Structure

```
report-engine/
├── report-engine-framework/     # 报表核心框架（声明式模型 + 内存渲染引擎）
├── report-engine-excel/         # Excel 构建/解析 + 字体管理（Apache POI 封装）
├── report-engine-starter/       # Spring Boot 自动配置（Controller + Bean 装配）
├── report-engine-example/       # 示例应用（仅启动类）
└── report-frontend/             # 前端 pnpm monorepo
    ├── packages/report-univer/  # Univer 电子表格 React 封装
    ├── packages/report-engine/  # 报表设计器组件库
    └── apps/app-pc/             # 演示应用
```

**依赖关系**：`example → starter → framework → excel`。framework 和 excel 可独立发布。

### Framework Architecture (`report-engine-framework`)

**核心设计**：声明式模型 + 内存计算。模板层（视觉呈现）与语义层（数据绑定与计算）完全分离。所有计算在 Java 内存完成（不下推 SQL），支持跨数据源 JOIN（如 MySQL + CSV）。

#### Package Organization (按业务域划分)

```
com.codingapi.report
├── data/              数据域：数据从哪来
│   ├── datamodel/     DataModel（可复用语义层）
│   ├── datasource/    DataSource + DataExtractor SPI
│   ├── dataset/       Dataset(sealed) → TableDataset / UnionDataset
│   └── relation/      Relationship（跨数据集，独立成域）
├── operator/          算子域：作用在 RawTable 行上的运算
│   ├── aggregation/   Aggregation + Aggregator SPI + 注册表
│   └── condition/     Condition + ConditionPredicate SPI + 注册表
├── expression/        表达式域：统一的"怎么算出一个值"机制
│   ├── Value(sealed)  值表达式树（8种节点）
│   ├── ExpressionEngine  注册表分发 + 递归求值
│   ├── eval/          各节点求值策略
│   └── function/      ValueFunction SPI + 注册表
├── param/             参数域：运行时值解析
└── render/            渲染域：数据如何映射到单元格
    ├── Report         报表定义
    ├── grid/          CellBinding（值层 + 控制层）
    └── engine/        ReportRenderer + Operators
```

**划分原则**：按真实业务域组织，父包要名副其实。`relation` 跨数据集所以独立；`operator` 是聚合算子 + 条件算子的共享抽象。

#### Expression Engine (统一取值机制)

`Value` 是 sealed interface，8 种节点类型：
- `Literal` / `FieldValue` / `ParamValue` / `LoopFieldValue` — 取值叶子
- `NameRef` — 晚绑定名字（`${name}` 编译成它，循环优先再参数）
- `Template(Text|Hole)` — 文本插值
- `Aggregate` — 聚合（SUM/COUNT/…）
- `FunctionCall` — 函数调用（format/date/…）

**求值策略**：每种节点对应一个 `ValueEvaluator` 实现，`ExpressionEngine` 按 `supports()` 选中分发。新增节点 = 新增实现 + 注册，零改动接入。

**文本语法**（`Templates.parse()`）：用户输入的 `${...}` 文本自动编译为 Value 树：
- `${name}` → `NameRef`（晚绑定）
- `${d.name}` → `FieldValue`（限定字段引用）
- `${COUNT(d.name)}` → `Aggregate`（聚合函数：COUNT/SUM/AVG/MAX/MIN/COUNT_DISTINCT）
- `${format(d.name)}` → `FunctionCall`（通用函数）
- `合计 ${SUM(d.salary)} 元` → `Template([Text, Hole(Aggregate), Text])`
- 纯文本无洞 → `Literal`；整个字符串一个洞 → 直接返回洞内 Value（不套 Template）

`Templates.containsAggregate(value)` 递归检测表达式树中是否含聚合节点，用于 `evalSingle` 判断走行集合还是首行上下文。

**扩展点统一范式**（`supports()` + 注册表）：
- `DataExtractor` — 新数据源类型
- `ConditionPredicate` — 新比较算子
- `Aggregator` — 新聚合方式
- `ValueFunction` — 新表达式函数

未注册的算子/聚合/函数会**显式抛异常**，不会静默放行。

#### CellBinding: 值层 + 控制层分离

`CellBinding` 是单类（不再是 sealed interface 的两个实现），拆成两件互不干扰的事：

- **值层** `value: Value` — 这个格子最终显示什么值（纯文本/字段/聚合/格式化），统一为表达式树
- **控制层** `expansion / expandMode / mergeRepeated / parentCell / conditions` — 值怎么在格子上铺开

渲染两层处理：① `ExpressionEngine.eval(value, ctx)` 算出数据 → ② 控制层按 expansion/merge 决定落格，样式从模板继承。

旧的 `TextCell` / `FieldCell` 子类型已删除（"用类型当值的开关"是坏味道）。`Condition` 左右值和 `SummaryCell` 的值也已归一到 `Value`。原 `ValueRef` 被 `Value` 取代后已删除。

#### Rendering Pipeline

```
render(dataModel, report, paramContext, templateWorkbook)
  1. seedTemplate    — 加载模板单元格/样式/合并区域到画布
  2. renderFree      — 非循环区：按数据集连通性分组 → 每组独立 JOIN+过滤+纵向带展开 → 文本插值
  3. renderLoop      — 循环块：提取驱动数据集 → 逐行迭代更新 ParamContext → 渲染块内格子
                      → 后续迭代按 rowOffset 复制模板合并区域（第 1 次由 seedTemplate 载入）
  4. buildWorkbook   — 画布 → Workbook 输出
```

**独立数据带**（`renderFree` 核心机制）：
- 用 union-find 按 `Relationship` 图将所有引用的 datasetId 分为连通分量
- 有关系的归入同组 → `buildCombinedTable` JOIN → 同行迭代
- 无关系的各自独立 → 各自 `buildCombinedTable` → 各自 `renderBand` → 行数可以不同
- 单值格（标题/总计等 NONE 格）用 `max(shifts)` 统一偏移
- 这支持"并列独立数据区"报表模式（如员工表 + 商品表并排，无 JOIN）

**样式继承**：`place()` 方法将值写入画布时，从 `canvas.template` 中查找源格（`CellBinding.cell` 坐标）并继承其 style。循环块内每次迭代都从同一模板源格继承样式。

#### Sealed Types (编译期穷尽)

- `Dataset` → `TableDataset`（物理表）/ `UnionDataset`（UNION 派生）
- `Value` → 8 种节点（见上）
- `ParamSource` → `External` / `Cell` / `Constant`

确保 `switch` / `instanceof` 覆盖所有子类型。

### Excel Module (`report-engine-excel`)

独立的 Excel 构建/解析库 + 字体管理。封装 Apache POI，提供 JSON ↔ .xlsx 双向转换。

- `ExcelExporter` / `ExcelImporter` — 互为逆操作
- `FontRegistry` — 双目录扫描（内置 + 自定义）、文件名前缀排序、JVM 注册
- `pojo/` — 数据模型：`Workbook → Sheet → Cell / Merge / Style → Font / Borders / RichText`

POJO 模型同时作为前后端 JSON 契约（Jackson 序列化兼容）。样式构建使用缓存机制（JSON 序列化作为 cache key），避免超出 POI 64K 样式上限。

### Starter Module (`report-engine-starter`)

Spring Boot 自动配置。注册 `FontRegistry` Bean、提供 `FontController` 和 `ExcelController` REST API。

- `GET /api/fonts/list` — 字体列表（排除内置，避免 @font-face 覆盖系统字体）
- `POST /api/excel/generate` — 生成 .xlsx 下载
- `POST /api/excel/import` — 导入 .xlsx

### Example Module (`report-engine-example`)

演示应用，同时承载**数据集配置层**、**报表配置持久化**和**报表渲染 API**（这些不属于通用 starter，是应用级逻辑）。

**数据集配置** (`DatasetConfig.java`)：
- 扫描 `classpath:data/*.json` 描述文件，每个 JSON 对应一个 CSV 数据集（字段名/别名/类型/主键）
- 每个数据集自动创建独立 `DataSource`（`config.path` 指向 CSV classpath 路径）
- `data/relationships.json` 定义跨数据集 JOIN（`Relationship` with `JoinType` + `RelationOrigin.MANUAL`）
- 构建唯一 `DataModel` Bean（id=`"default"`），全局注入

**报表配置持久化** (`ReportConfigController.java`)：
- `POST /api/report/configs` — 保存报表配置（含 id 则更新），返回报表 id
- `GET /api/report/configs/{id}` — 加载完整配置，**附带 `dataModel` 字段**（datasets + relationships，由后端从 `DataModel` Bean 解析）
- `GET /api/report/configs/examples` — 示例报表列表（预存的测试报表 id + name）
- `GET /api/report/configs` — 所有报表列表（id + name）
- 配置以 `Map<String, Object>` 存储（内存 `ConcurrentHashMap`），包含 name/dataModelId/cellBindings/loopBlocks/summaries/params/template

**示例报表预存** (`ReportTemplateSeeder.java`)：
- 启动时（`ApplicationReadyEvent`）向 `ReportRepository` 写入 7 个完整报表配置
- 涵盖：简单列表、分组列表、多级分组统计、主从合并、小计+总计、薪资条循环、独立数据带并列
- 每个配置包含完整的 cellBindings + template（ExcelWorkbook），前端可直接导航加载

**报表渲染 API** (`ReportRenderController.java`)：
- `POST /api/report/render` — 接收前端配置 + 模板快照 → 调用 `ReportRenderer.render()` → 返回填充数据的 `.xlsx`
- 使用 DTO 层（`RenderRequest` / `BindingDTO` / `ValueDTO` 等）匹配前端 JSON 格式
- 内部转换为 framework 领域对象（`CellBinding` / `Value` / `LoopBlock` / `SummaryRow`）
- `Value` 等 sealed interface **未加 Jackson 多态注解**，所以用 DTO 中间层而非直接反序列化

**数据集 API** (`DatasetController.java`)：
- `GET /api/datasets` — 数据集列表（含字段定义），供前端左面板树形展示
- `GET /api/datasets/{id}/preview?limit=20` — 预览前 N 行数据

**CSV 数据集**（`src/main/resources/data/`）：10 个 CSV + 对应 JSON 描述 + `relationships.json`。

### Frontend Architecture

**技术栈**：React 18 + TypeScript 5.9 + Ant Design 6 + Univer 0.25（插件模式）+ pnpm 10 workspaces。

**构建顺序**：`report-univer` → `report-api` → `report-engine`（pnpm build 脚本已处理）。

#### Package 职责

- **`report-univer`**：Univer 电子表格 React 封装。提供 `UniverSheet` 组件 + `UniverSheetHandle` 命令式句柄（`getSnapshot` / `loadSnapshot` / `setCellValue` / `getActiveSheetId`）。三层属性存储（cellProps / mergeProps / loopBlockProps）通过泛型自定义。
- **`report-api`**：后端 API 客户端。axios 实例（`baseURL: '/api'`）+ 响应拦截器自动解包 `SingleResponse` / `MultiResponse`。暴露 `saveReportConfig` / `loadReportConfig` / `listExampleReports` / `renderReport` / `exportExcel` / `importExcel` / `fetchFonts`。
- **`report-engine`**：报表设计器组件库（纯 UI，不直接调 API）。

#### ReportEngine 组件

**Props 驱动**：`datasets` + `relationships` + `dataModelId` + `functions`（公式目录）+ `onExport` / `onImport` / `onSaveReport` / `onFontRequest` 回调。数据由 app-pc 从 API 获取后传入。

**三栏式布局**：
- 左面板 `DataModelPanel`：数据集 / 数据关系 / 报表参数 三 tab
- 中面板 `SheetPanel`：`forwardRef` 封装 UniverSheet，暴露 `getActiveSheetId()` 获取实际 sheet ID
- 右面板 `PropertyPanel`：选中单元格的绑定编辑器

**配置加载与保存**：
- `loadReportConfig(config)` — 加载快照 → 获取 Univer 实际 sheet ID → 重映射所有 cellKey → 回写绑定显示文本（`valueDisplayText`）
- `handleSaveReport` — 收集 `getSnapshot()` + cellBindings/loopBlocks/summaries/params + `dataModelId` → 调 `onSaveReport` 回调
- `ReportConfig` 持久化结构：`id / name / dataModelId / cellBindings / loopBlocks / summaries / params / template(ExcelWorkbook)`

**模板预设**（`TemplatePreset` 接口 + `applyTemplate`）仍保留为组件能力，但 app-pc 已改为后端预存示例报表 + 导航加载模式。

**类型体系**：枚举值使用大写字符串联合类型（`'VERTICAL'` / `'SUM'` / `'FieldValue'`），对齐 Java enum `name()`。

#### Excel 数据流

`report-univer` 提供双向快照能力，与后端共享同一 JSON 结构（`ExcelWorkbook`）。
- **报表配置流程**：首页选择示例报表或创建新报表 → `/engine?id=xxx` → `loadReportConfig(GET /configs/{id})` → 设计编辑 → `saveReportConfig(POST /configs)` 持久化
- **导出渲染报表**：`getSnapshot() → renderReport({ cellBindings, loopBlocks, summaries, template }) → POST /api/report/render → .xlsx Blob`
- **导出空模板**：`getSnapshot() → exportExcel(workbook) → POST /api/excel/generate → .xlsx Blob`
- **导入**：`POST /api/excel/import → JSON → loadSnapshot()`

**字体管理**：`UniverSheet` 通过 `onFontRequest` 回调向父组件请求字体数据，内部自动处理 localStorage 缓存 → @font-face 注入 → `addFonts()` 注册。

## Testing

- 后端 framework 和 excel 模块的测试均**不需要外部依赖**（纯内存/POI 测试），可直接运行
- framework 测试使用 CSV 文件（`src/test/resources/data/`）作为数据源，`ReportScenarioTest` 覆盖 7 种报表结构场景 + 独立数据带场景
- 前端库包使用 `bundle: false`（非打包模式），保留 tree-shaking 能力

## Key Design Decisions

1. **内存计算优先**：所有计算在 Java 内存完成（不下推 SQL），支持跨数据源 JOIN
2. **模板层与语义层分离**：视觉呈现与数据绑定完全解耦，渲染时合并
3. **表达式统一**：单元格值、条件左右值、小计标签/聚合全部归一到 `Value` 表达式树
4. **策略机制扩展**：算子/聚合/函数/数据提取统一用 `supports()` + 注册表范式
5. **密封类型穷尽**：`Dataset` / `Value` / `ParamSource` 使用 sealed interface，编译期强制覆盖

## Notes

- 使用 Lombok（`@Data` / `@Builder`），但部分类未加注解导致缺少 getter/setter
- framework 大量使用 Java 17 sealed interface + record
- 前端 `UniverSheet` 裁剪了大量菜单项，仅保留报表设计所需的最小功能集
- **跨模块修改**：修改 framework/excel/starter 后必须 `./mvnw install -DskipTests` 再启动 example，否则 example 使用的是本地仓库中的旧 JAR
- **Univer 默认 sheet ID**：UniverSheet 创建的默认 sheet ID 不一定是 `'sheet1'`（可能是 UUID），需要通过 `getActiveSheetId()` 从快照获取实际 ID
- **Value sealed interface 无 Jackson 注解**：前后端传输使用 DTO 层（`ValueDTO` 等）中间转换，而非直接在 Value 上加 `@JsonTypeInfo`
- **模板层样式继承**：`renderLoop` 中后续迭代的合并区域需要显式复制（`seedTemplate` 只载入原始位置的 merge），样式通过 `place()` 从模板源格继承
- **loadReportConfig sheet ID 重映射**：后端存储的 cellKey 中 sheet ID 可能是 `"sheet1"`，但 Univer 运行时活动工作表 ID 可能是 UUID。`loadReportConfig` 必须先 `getActiveSheetId()` 获取实际 ID，再重映射所有 cellKey/parentCell，最后回写绑定显示文本
- **数据模型随配置加载**：`GET /configs/{id}` 返回的 `dataModel` 字段由后端从注入的 `DataModel` Bean 实时解析（当前始终返回唯一的全局模型），配置中仅存 `dataModelId` 引用
