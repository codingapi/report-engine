# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Behavioral Guidelines

**Bias toward caution over speed.** These guidelines reduce common LLM coding mistakes.

1. **Think Before Coding** — State assumptions explicitly. Surface tradeoffs. Ask before picking silently.
2. **Simplicity First** — Minimum code that solves the problem. No speculative features or abstractions for single-use code.
3. **Surgical Changes** — Touch only what you must. Don't "improve" adjacent code. Remove only orphans YOUR changes created.
4. **Goal-Driven Execution** — Define success criteria. Transform tasks into verifiable goals. Loop until verified.
5. **代码提交纪律** — 完成修改后不要立即 git commit/push，等用户确认后再提交。先展示变更摘要。
6. **优先使用 antd 组件** — 凡引入 antd 的前端项目，开发组件时先检查 antd 是否已有可直接使用的组件（如 Empty、List、Tag、Descriptions、Steps 等）。优先用 antd 原生组件实现，不自造轮子；仅当 antd 确实无法满足需求时才自行封装。

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
├── report-engine-starter/       # Spring Boot 自动配置 + 全部通用 REST API + 存储/DTO 抽象
├── report-engine-example/       # 示例应用（数据集配置 + 示例报表预存等应用级实现）
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
- **控制层** `expansion / expandMode / mergeRepeated / parentCell / conditions / independent` — 值怎么在格子上铺开

`independent`（默认 false）：同一数据集的纵向列**默认对齐成一条带**（一行一记录，列共享行）。置 true 时该列从自身声明行**独立向下展开**（交错/错位排版），不参与对齐带的位移/汇总/merge 下移，也无法再与别列做跨列聚合/跨列汇总/主从合并。`renderFree` 中由 `groupIndependentByRow` 按声明行分组、各自 `renderBand`。

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

**合并区边框补全**：POI 中合并单元格只有左上锚点格样式生效，边框只设在锚点格会导致 Excel 仅在左上角描出残缺边框。`ExcelExporter.applyMergeBorders` 把锚点格的边框按位置铺满整个合并区周边（顶边铺首行、底边铺末行、左右边铺首末列），保留 RGB 颜色与锚点的字体/填充。

### Starter Module (`report-engine-starter`)

Spring Boot 自动配置 + **全部通用 REST API**。API 是 Spring Bean（不能放 framework），故落在 starter。`ReportEngineAutoConfiguration` 用 `@Bean` + `@ConditionalOnMissingBean` 注册，使用方（如 example）可覆盖任意 Bean。

**基础设施 API**：
- `GET /api/fonts/list` — 字体列表（排除内置，避免 @font-face 覆盖系统字体）
- `POST /api/excel/generate` / `POST /api/excel/import` — Excel 导出/导入

**报表引擎 API**（`controller/`）：
- `POST /api/report/render`（`ReportRenderController`）— 配置 + 模板快照 → `ReportRenderer.render()` → 填充数据的 `.xlsx`
- `/api/report/configs[...]`（`ReportConfigController`）— 报表配置保存（`@RequestBody ReportConfig`）/加载（附带 `dataModel` 富化）/分页列表（`page(SearchRequest): Page<ReportConfig>`）/删除
- `GET /api/datamodels`（`DataModelController`）— 数据模型列表（注入 `List<DataModel>`，供创建报表时选择）
- `GET /api/datasets[...]`（`DatasetController`）— 数据集列表（含字段）/预览前 N 行
- `GET /api/expression/functions`（`ExpressionController`）— 公式目录（聚合 + 函数元信息）

**存储抽象**（接口在 starter，实现由使用方提供）：
- `ReportRepository` 接口（`com.codingapi.report.starter.repository`）：`save(ReportConfig):String` / `find(id):ReportConfig` / `page(SearchRequest):Page<ReportConfig>` / `delete(id)`。starter **不提供默认实现**（存储交使用方），用 `@ConditionalOnMissingBean` 装配 Controller 允许覆盖。
- `ReportConfig` 实体在 **framework**（`com.codingapi.report.config`）：强类型 POJO，`id/name/dataModelId/createTime/updateTime`（long 时间戳）+ `cellBindings/loopBlocks/summaries/params/template`（引用 DTO record）+ `dataModel`（响应富化字段，`@JsonInclude NON_NULL`，仅 GET 返回不持久化）。example 的 `InMemoryReportRepository` 在 save 时设时间戳、null 列表归一空。
- 接口放 starter 而非 framework：因 `page(SearchRequest)` 需 `SearchRequest`/`Page` 这类 Spring 类型，放 framework 会引入 Spring 依赖、破坏"framework 可独立发布"原则。

**DTO 契约层**（DTO record 在 **framework** `com.codingapi.report.config.dto.ConfigDtos`，转换在 starter `RenderDtoConverter`）：
- DTO record（`BindingDTO`/`ValueDTO`/`LoopBlockDTO`/`SummaryRowDTO`/`SummaryCellDTO`/`ConditionDTO`/`PartDTO`/`SourceDTO`/`FieldRefDTO`）同时是 `ReportConfig` 实体的持久化字段类型 + 前端 JSON 契约。
- 前端 JSON → DTO record（Jackson）→ framework 领域对象（`CellBinding`/`Value`/`LoopBlock`/`SummaryRow`）由 `RenderDtoConverter` 转换。
- `Value` 等 sealed interface **未加 Jackson 多态注解**，故用 DTO 中间层而非直接反序列化；`RenderDtos` 仅剩 `RenderRequest`（渲染请求，`params` 为运行时值 Map，与实体的 `params` 定义不同）。
- ⚠️ **给 `CellBinding` 加字段要同步五处**（否则字段会在某条链路被悄悄丢弃）：framework `ConfigDtos.BindingDTO`、starter `RenderDtoConverter.convertBindings`、前端 `report-engine` 的 `CellBinding` 类型、**`report-api` 的 `RenderBindingDTO` + `app-pc` 的 `toBindingDTO`**（渲染走显式字段映射，与保存走原始对象是两条独立链路）。`SummaryRow.id` 也在 DTO 中持久化。

### Example Module (`report-engine-example`)

演示应用，只承载**应用级实现**：数据集配置（具体数据）、示例报表预存。通用 API / 存储 / DTO 均在 starter。

**数据集配置** (`DatasetConfig.java`)：
- 扫描 `classpath:data/*.json` 描述文件，每个 JSON 对应一个 CSV 数据集（字段名/别名/类型/主键）
- 每个数据集自动创建独立 `DataSource`（`config.path` 指向 CSV classpath 路径）
- `data/relationships.json` 定义跨数据集 JOIN（`Relationship` with `JoinType` + `RelationOrigin.MANUAL`）
- 构建唯一 `DataModel` Bean（id=`"default"`）+ `CsvDataExtractor` Bean，注入 starter 的 Controller

**示例报表预存** (`ReportTemplateSeeder.java`)：
- `@Component`，启动时（`@EventListener(ApplicationReadyEvent)`）向 `ReportRepository`（example 内存实现）写入 8 个完整报表配置
- 示例报表使用**写死的稳定 id**（`example-simple-list` 等），保证重启后 id 不变、前端引用不失效（内存存储重启即丢，但 seeder 用同一批 id 重写）
- 涵盖：简单列表、分组列表、多级分组统计、主从合并、小计+总计、薪资条循环、独立数据带并列、并列双汇总
- 配置用 `ReportConfigBuilder`（链式构造器）生成，产物为强类型 `ReportConfig`（引用 framework DTO record + `Workbook` POJO）。示例列表不再有专用端点，统一走 starter 的 `GET /api/report/configs`（示例与用户报表同表）。

**CSV 数据集**（`src/main/resources/data/`）：10 个 CSV + 对应 JSON 描述 + `relationships.json`。

### Frontend Architecture

**技术栈**：React 18 + TypeScript 5.9 + Ant Design 6 + Univer 0.25（插件模式）+ pnpm 10 workspaces。

**构建顺序**：`report-univer` → `report-api` → `report-engine`（pnpm build 脚本已处理）。

#### Package 职责

- **`report-univer`**：Univer 电子表格 React 封装。提供 `UniverSheet` 组件 + `UniverSheetHandle` 命令式句柄（`getSnapshot` / `loadSnapshot` / `setCellValue` / `getActiveSheetId`）。三层属性存储（cellProps / mergeProps / loopBlockProps）通过泛型自定义。
- **`report-api`**：后端 API 客户端。axios 实例（`baseURL: '/api'`）+ 响应拦截器自动解包 `SingleResponse` / `MultiResponse`。暴露 `saveReportConfig` / `loadReportConfig` / `deleteReportConfig` / `listReportConfigs(current,pageSize)` / `listDataModels` / `renderReport` / `previewReport` / `drillReport` / `exportExcel` / `importExcel` / `fetchFonts`。
- **`report-engine`**：报表设计器组件库（纯 UI，不直接调 API）。核心导出：`ReportEngine`（设计器）、`ReportPreview`（预览能力组件，含参数弹窗+预览抽屉+反查+抽屉内导出）、`useReportPreview` hook。

#### ReportEngine 组件

**Props 驱动**：`datasets` + `relationships` + `dataModelId` + `functions`（公式目录）+ `renderService`（注入 report-api 的 `previewReport`/`renderReport`/`drillReport`，启用内置预览/导出全流程）+ `onImport` / `onSaveReport` / `onFontRequest` 回调。数据由 app-pc 从 API 获取后传入。

**顶部按钮可配置**（`re-header__actions`，宽度统一 112px）：
- 默认按钮组：`导入模板`/`循环块`/`报表预览`/`导出报表`/`保存报表`，各受 `enableImport`/`enableLoopBlock`/`enablePreview`/`enableExport`/`enableSave`（默认 `true`）控制，且受 `onImport`/`renderService`/`onSaveReport` 前置条件约束（取交集）。
- `customActions?: ReactNode` — 渲染在默认组**左侧**（导入模板左），有内容时加竖线分隔。
- `extraActions?: ReactNode` — 渲染在默认组**右侧**（保存报表右）。
- 布局：`[customActions] | 导入模板 | 循环块 | 报表预览 | 导出报表 | 保存报表 | [extraActions]`

**三栏式布局**：
- 左面板 `DataModelPanel`：数据集 / 数据关系 / 报表参数 三 tab
- 中面板 `SheetPanel`：`forwardRef` 封装 UniverSheet，暴露 `getActiveSheetId()` 获取实际 sheet ID
- 右面板 `PropertyPanel`：选中单元格的绑定编辑器

**配置加载与保存**：
- `loadReportConfig(config)` — 加载快照 → 获取 Univer 实际 sheet ID → 重映射所有 cellKey → 回写绑定显示文本（`valueDisplayText`）
- `handleSaveReport`（`use-report-io`）— 收集 `getSnapshot()` + cellBindings/loopBlocks/summaries/params + `dataModelId` → 调 `onSaveReport` 回调；保存前剥离 `displayText`、对模板应用 `templateToString`
- `ReportConfig` 持久化结构（framework 实体）：`id / name / dataModelId / createTime / updateTime(long 时间戳) / cellBindings / loopBlocks / summaries / params / template(Workbook)` + `dataModel`(响应富化，不持久化)。时间戳由 `InMemoryReportRepository.save` 设置。

**模板预设**（`TemplatePreset` 接口 + `applyTemplate`）仍保留为组件能力，但 app-pc 已改为后端预存示例报表 + 导航加载模式。

**类型体系**：枚举值使用大写字符串联合类型（`'VERTICAL'` / `'SUM'` / `'FieldValue'`），对齐 Java enum `name()`。

#### Excel 数据流

`report-univer` 提供双向快照能力，与后端共享同一 JSON 结构（`ExcelWorkbook`）。
- **app-pc 路由**：`/`（首页）+ `/reports`（报表管理，antd Table 分页）+ `/engine`（设计器 `AppReport`）+ `/preview`（预览页 `AppPreview`），仅首页/报表管理在菜单。
- **报表管理流程**：`/reports` 表格列全部报表（`listReportConfigs(current,pageSize)`，按 `SearchRequest` 分页）→ 新建弹窗（名称 + 数据模型，`listDataModels`）→ 操作列「编辑/预览/删除」（a 标签）。
- **报表配置流程**：`/engine?id=xxx`（`AppReport`）→ `loadReportConfig(GET /configs/{id})` → 设计编辑 → `saveReportConfig(POST /configs)` 持久化。
- **独立预览流程**：`/preview?id=xxx`（`AppPreview`）→ `loadReportConfig` → 挂 `ReportPreview` 组件（`config` 触发预览，`onClose` 返回报表管理）。
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
- **Value sealed interface 无 Jackson 注解**：前后端传输/持久化使用 DTO record（在 framework `ConfigDtos`，同时是 `ReportConfig` 实体字段类型）+ starter `RenderDtoConverter` 中间转换，而非直接在 Value 上加 `@JsonTypeInfo`。`ReportRepository` 接口放 starter（需 `SearchRequest`/`Page`），`ReportConfig` 实体放 framework（纯 POJO，仅依赖 excel 的 `Workbook`），保持 framework 可独立发布。
- **模板层样式继承**：`renderLoop` 中后续迭代的合并区域需要显式复制（`seedTemplate` 只载入原始位置的 merge），样式通过 `place()` 从模板源格继承
- **loadReportConfig sheet ID 重映射**：后端存储的 cellKey 中 sheet ID 可能是 `"sheet1"`，但 Univer 运行时活动工作表 ID 可能是 UUID。`loadReportConfig` 必须先 `getActiveSheetId()` 获取实际 ID，再重映射所有 cellKey/parentCell，最后回写绑定显示文本
- **数据模型随配置加载**：`GET /configs/{id}` 返回的 `dataModel` 字段由后端从注入的 `DataModel` Bean 实时解析（当前始终返回唯一的全局模型），配置中仅存 `dataModelId` 引用
- **带扩展的内容下移（renderFree）**：纵向带扩展会把带下方的内容整体下移。下移量 = 输出行数 −（带声明行 + 汇总声明行），**汇总行不重复计入扩展量**（否则带下内容多移一行）。需随带下移的不只单值绑定与模板 merge，还包括**无绑定的静态模板格**（标题/页脚，3c）和**模板行高配置**（3d）；汇总行的 merge/静态格按 `summaryOutputRows` 精确归位
- **带声明格残留清理（renderFree 3e）**：带统一从 bandBase 起填充，若某绑定声明格未被数据覆盖（如声明在 B12 但带从 row0 展开），其设计期占位文本会残留 → 渲染后清除所有"未被动态写入"的带声明格
- **模板行高/列宽带出**：`buildWorkbook` 会从模板 sheet 带出 `defaultRowHeight/defaultColumnWidth/columns/rows`（早期版本会丢弃）；行高随带扩展按同一位移规则跟随
- **汇总行样式继承**：`summaryOut` 给汇总单元格设置 `source`（指向模板"汇总声明行"），使汇总行渲染时通过 `place()` 继承模板样式（边框/字体随汇总滚动到输出位置）
- **单元格变更同步（report-univer）**：监听 `set-range-values` mutation 同步内容到汇总行时，**必须用 `'v' in cell` 判定**是否真有值变更——边框/填充等纯样式 mutation 只带 `s` 不带 `v`，若误读为空字符串会把已设内容同步清空（删除内容时 Univer 会显式带 `v: null`，仍判定为真，不受影响）
- **展示/真实值分轨（前端 report-engine）**：单元格「显示别名、传输真实 ID」。`value.payload` 是真实 ID（权威，用于传输/导出）；`CellBinding`/`SummaryCell.displayText` 是别名展示文本（**transient**，由 `valueDisplayText(value)` 正向派生，用于①写进单元格显示 ②回声判别）。**后端完全不接收、不存储 `displayText`**（与需同步五处的渲染契约字段不同），保存出口（`use-report-io` 的 `handleSaveReport` / `getReportConfig`）剥离。展示永远由 `value` **正向**派生，**绝不反解 `displayText` → `value`**——别名→ID 是多对一（可重名），反向有歧义
- **回声判别替代 isLoadingRef（`handleCellValueChange`）**：往单元格写显示文本会触发 `set-range-values` mutation，需区分「程序回写」与「用户手敲」。用 `displayText` 作基准：新文本 `=== displayText` ⇒ 回声，忽略；`=== ''` ⇒ 清空，移除绑定；否则用户手敲 → 纯文本/模板格退化为 `Literal`（**不反解别名、不构造引用洞**），字段/聚合/参数等引用格保护不覆盖（改表达式走属性面板/拖拽）。基于数据基准而非时序标志，不依赖 mutation 是否同步派发
- **预览能力下沉（report-engine `ReportPreview` 组件）**：参数弹窗→渲染→预览抽屉→反查→抽屉内导出全流程封装为 `ReportPreview`（`components/preview/preview.tsx`），设计器与独立预览页共用。`useReportPreview` hook 只管逻辑/状态，JSX 在组件层渲染（含私有 `WorkbookTable` 画表格，不单独导出）。**纯 UI 不调 API**：通过 `renderService` prop 注入 report-api 的 `previewReport`/`renderReport`/`drillReport`（只导类型，不导函数）。设计器点「预览」→ `setPreviewConfig(newConfig)`（引用变化触发）；预览页加载配置后 `setPreviewConfig(loaded)`。`ReportPreview` 用 `lastPreviewedRef` 去重避免 strict-mode 重复触发；预览页加载 effect 用局部 `active` 标志（**勿用跨渲染 `startedRef`**，否则 strict-mode 双调用会让预览永不执行）。抽屉 `onClose` 回调（`ReportPreview` 的 `onClose` prop）：设计器不传（停在设计器），预览页传 `navigate('/reports')`。
- **已保存配置即渲染就绪态**：`handleSaveReport` 保存时已对模板应用 `templateToString` 并剥离 `displayText`，故 `loadReportConfig` 返回的配置可直接喂给 `ReportPreview`（无需 `collectRenderArgs` 预处理；`preview` 字段可选，后端仅存储不渲染）。
