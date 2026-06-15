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
  1. seedTemplate    — 加载模板单元格/样式到画布
  2. renderFree      — 非循环区：提取数据集 → greedy-join → 按条件过滤 → 纵向带展开 → 文本插值
  3. renderLoop      — 循环块：提取驱动数据集 → 逐行迭代更新 ParamContext → 渲染块内格子
  4. buildWorkbook   — 画布 → Workbook 输出
```

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

### Frontend Architecture

**技术栈**：React 18 + TypeScript 5.9 + Ant Design 6 + Univer 0.25（插件模式）+ pnpm 10 workspaces。

**构建顺序**：必须先构建 `report-univer`，再构建 `report-engine`（pnpm build 脚本已处理）。

**三栏式布局**（`ReportEngine` 组件）：
- 左面板：数据源树形浏览
- 中面板：Univer 电子表格（支持单元格选中、右键菜单、循环块配置）
- 右面板：单元格属性配置（条件 + 计算方式）

状态管理使用纯 React hooks，无外部状态库。

**API 响应结构**：后端统一使用 `SingleResponse<T>` / `MultiResponse<T>` / `MapResponse` 包装。前端 axios 拦截器自动解包（检测 `success` 字段）。

**Excel 数据流**：`report-univer` 提供双向快照能力，与后端共享同一 JSON 结构（`ExcelWorkbook`）。导出：`getSnapshot() → extractSnapshot() → JSON → POST /api/excel/generate`。导入：`POST /api/excel/import → JSON → loadSnapshot()`。

**字体管理**：`UniverSheet` 框架内置字体加载能力，与后端解耦。通过 `onFontRequest` 回调向父组件请求字体数据，内部自动处理 localStorage 缓存 → @font-face 注入 → `addFonts()` 注册。字体文件 URL 由后端 API 返回，框架不硬编码路径。

## Testing

- 后端 framework 和 excel 模块的测试均**不需要外部依赖**（纯内存/POI 测试），可直接运行
- framework 测试使用 CSV 文件（`src/test/resources/data/`）作为数据源，覆盖 7 种报表场景
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
