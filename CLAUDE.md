# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 5. 代码提交纪律

**完成修改后不要立即 git commit/push，等用户确认后再提交。**

- 代码改完后先展示变更摘要，等用户说"提交"或"commit"再执行
- 除非用户明确允许，不要自动提交和推送

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## 项目概述

Report Engine 是一个报表引擎框架，支持用户通过电子表格界面设计报表模板，配置数据源、条件规则和计算方式，最终生成数据报表。前后端一体化仓库管理，处于早期开发阶段 (v0.0.1)。

## 构建与运行命令

### 后端 (Java 17 + Maven)

```bash
# 编译全部模块（framework + excel + example）
./mvnw clean compile

# 运行 excel 模块测试（纯 POI 测试，无外部依赖）
./mvnw test -pl report-engine-excel

# 运行 framework 模块测试（需要本地 PostgreSQL: localhost:5432/report）
./mvnw test -pl report-engine-framework

# 运行单个测试类
./mvnw test -pl report-engine-excel -Dtest=ExcelImporterTest

# 仅编译 travis profile（framework 模块 + JaCoCo 覆盖率）
./mvnw clean test -P travis

# 发布到 Maven Central（需要 GPG 签名）
./mvnw clean deploy -P ossrh

# 启动 example 应用（端口 8080）
./mvnw spring-boot:run -pl report-engine-example
```

### 前端 (pnpm monorepo)

```bash
cd report-frontend

# 安装依赖
pnpm install

# 按顺序构建所有库包（report-univer → report-engine）
pnpm build

# 开发模式 watch（库包变更后自动重新编译）
pnpm watch:report-univer
pnpm watch:report-engine

# 启动演示应用（app-pc，会打开浏览器）
pnpm dev:app-pc

# 构建并发布到 npm
pnpm push
```

## 项目结构

```
report-engine/
├── report-engine-framework/     # 后端核心框架（数据源/组件/元数据，可发布到 Maven Central）
├── report-engine-excel/         # Excel 构建与解析模块（Apache POI 封装 + 字体管理）
├── report-engine-starter/       # Spring Boot 自动配置模块（Controller + Bean 装配）
├── report-engine-example/       # 后端示例 Spring Boot 应用（仅含启动类）
└── report-frontend/             # 前端 pnpm monorepo
    ├── packages/report-univer/  # @coding-report/report-univer — Univer 电子表格 React 封装
    ├── packages/report-engine/  # @coding-report/report-engine — 报表设计器核心组件库
    └── apps/app-pc/             # @report-example/app-pc — 演示应用
```

## 后端架构

### 模块关系

```
report-engine-example (演示应用)
  ├── report-engine-starter (自动配置 + REST API)
  │     └── report-engine-excel (Excel + 字体)
  └── report-engine-framework (数据源/组件)
```

- `report-engine-excel`：独立的 Excel 构建/解析库 + 字体管理。封装 Apache POI，提供 JSON ↔ .xlsx 双向转换。不依赖 Spring，纯 Java 实现
- `report-engine-starter`：Spring Boot 自动配置模块。注册 FontRegistry Bean、提供 FontController 和 ExcelController REST API。`@ConditionalOnClass(RestController.class)` 控制 Web 组件按需装配
- `report-engine-framework`：报表核心框架（数据源、组件体系、元数据扫描），自动配置通过 `AutoConfiguration.imports` 注册
- `report-engine-example`：仅含 `ServerApplication` 启动类，业务逻辑全部在 starter/framework 中

### report-engine-excel 模块 (`com.codingapi.report.excel`)

| 类/包 | 职责 |
|---|---|
| `ExcelExporter` | `Workbook` POJO → `.xlsx` 字节流（POI 构建） |
| `ExcelImporter` | `.xlsx` 字节流 → `Workbook` POJO（POI 解析），与 Exporter 互为逆操作 |
| `FontRegistry` | 字体管理：双目录扫描（内置 + 自定义）、classpath 资源提取、文件名前缀排序、JVM 注册 |
| `pojo/` | 数据模型：`Workbook` → `Sheet` → `Cell` / `Merge` / `Row` / `Column`；`Style` → `Font` / `Borders` / `Padding` / `RichText`；`FontInfo` 字体元数据 |

POJO 模型同时作为前后端 JSON 契约：前端 `ExcelWorkbook` TypeScript 类型与后端 `Workbook` POJO 的字段名一一对应，Jackson 序列化/反序列化保持兼容。

**样式支持**：字体（family/size/bold/italic/underline/strikethrough/color）、对齐（5 种水平 + 3 种垂直）、边框（13 种线型 + 颜色）、填充、旋转、换行、数字格式、富文本分段样式、缩进。样式构建使用缓存机制（JSON 序列化作为 cache key），避免超出 POI 64K 样式上限。

**单位转换**：行高使用 pixels ↔ points（96DPI → 72DPI），列宽使用 pixels ↔ width units（×256/7）。

### report-engine-starter 模块 (`com.codingapi.report.starter`)

| 类/包 | 职责 |
|---|---|
| `ReportEngineAutoConfiguration` | 主配置类：注册 FontRegistry Bean，内部 WebConfiguration 注册 Controller |
| `properties/ReportFontProperties` | 配置属性：`report.fonts.dir` 指定用户自定义字体目录 |
| `controller/FontController` | 字体 API：`GET /api/fonts/list`（MultiResponse）+ `GET /api/fonts/file/{filename}`（文件下载） |
| `controller/ExcelController` | Excel API：`POST /api/excel/generate`（二进制下载）+ `POST /api/excel/import`（SingleResponse） |

### 字体管理系统

**FontRegistry** 支持双目录模式：
- **内置字体**（`resources/fonts/`）：通过 `extractBuiltinFonts()` 加载，开发模式直接引用 `target/classes/fonts/`，JAR 部署时提取到临时目录
- **自定义字体**（`report.fonts.dir` 配置）：扫描用户指定目录
- **排序**：通过文件名数字前缀（如 `01_微软雅黑.ttf` → order=1），无编号按文件名自然序排后
- **API**：`FontController.listFonts()` 按族名去重、排除内置字体（避免 @font-face 覆盖系统字体）

**前端字体加载**（`report-univer` 框架内处理）：
- `UniverSheet` 通过 `onFontRequest` 回调向父组件请求字体数据
- 内部自动处理：localStorage 缓存 → .ttc 过滤 → @font-face 注入 → `addFonts()` 注册
- 父组件（如 `univer-test.tsx`）只负责调用 API 返回 `FontItem[]`（含 family/filename/url）
- 字体文件 URL 由后端 API 返回（`FontItem.url`），框架不硬编码路径

### framework 核心包结构 (`com.codingapi.report`)

| 包 | 职责 |
|---|---|
| `components/` | 报表组件体系：`IComponent` → `Text` / `Image` / `Table` / `Layout` |
| `content/` | 内容标记接口：`IContent` → `TextContent` / `DataContent` |
| `data/` | 数据源查询链：`IDataSource` → `BaseDataSource` → `SQLDataSource` |
| `context/` | `JdbcTemplateContext` 单例，封装 JDBC 查询和元数据扫描 |
| `meta/` | 数据库元数据模型：`DBTable` / `DBColumn` / `DBColumnKey` / `DataType` 枚举 |
| `scanner/` | `DBScanner` 通过 JDBC DatabaseMetaData 扫描表/列/主键/外键 |
| `paper/` + `panel/` | 纸张和面板容器模型 |
| `display/` | 布局系统：`Display` → `FlexDisplay` |
| `stype/` | 样式定义（注意包名是 `stype` 而非 `style`） |
| `format/` | 数据格式化：`DataFormat` → `StringFormat` |

### 自动配置机制

`AutoConfiguration` → `JdbcTemplateContextRegister`：当 classpath 中存在 `JdbcTemplate` 时，自动将其实例注入到 `JdbcTemplateContext` 单例。同时注册了 Spring Boot 2.x (`spring.factories`) 和 3.x (`AutoConfiguration.imports`) 两种格式。

### 数据查询流程

`SQLDataSource.data(params)` → `BaseDataSource.verifyParams()` 校验 → `BaseDataSource.toParamArgs()` 转换 → `JdbcTemplateContext.queryForList(sql, args)` 执行查询 → 返回 `DataResult`(内含 `List<DataRow>` → `List<DataItem>`)

### 测试数据

测试使用 JPA 实体（学生/班级/成绩/考试/科目），通过 `DataService` 初始化数据，验证 SQL 查询和元数据扫描功能。

## 前端架构

### 包依赖关系

```
app-pc (演示应用, Rsbuild)
  └── @coding-report/report-engine (组件库, Rslib, bundle:false)
        └── @coding-report/report-univer (Univer 封装, Rslib, bundle:false)
              └── @univerjs/* v0.25
```

构建顺序：必须先构建 `report-univer`，再构建 `report-engine`（pnpm build 脚本已处理）。

### 技术栈

- React 18 + TypeScript 5.9 + Ant Design 6
- 电子表格引擎：Univer 0.25（插件模式，手动注册 14 个插件，见 `setup.ts`）
- 面板布局：`react-resizable-panels`
- 响应式：RxJS 7.8
- 构建工具：Rslib（库包）+ Rsbuild（应用）
- 测试框架：Rstest + @testing-library/react + happy-dom
- 包管理：pnpm 10 workspaces

### ReportEngine 组件（核心）

三栏式报表设计器布局：
- **左面板**（`DataSourcePanel`）：数据源树形浏览，antd Tree 展示表/字段/主键/外键
- **中面板**（`SheetPanel`）：Univer 电子表格，支持单元格选中、右键菜单、循环块配置
- **右面板**（`PropertyPanel`）：单元格属性配置（X/Y轴条件 + 计算方式）

状态管理使用纯 React hooks（useState/useCallback），无外部状态库。

### API 响应结构规范

后端 JSON 接口统一使用 `com.codingapi.springboot.framework.dto.response` 包装：

| 场景 | 类型 | JSON 结构 |
|------|------|----------|
| 无数据 | `Response` | `{ success, errCode?, errMessage? }` |
| 单对象 | `SingleResponse<T>` | `{ success, data: T }` |
| 列表 | `MultiResponse<T>` | `{ success, data: { total, list: T[] } }` |
| KV 字典 | `MapResponse` | `{ success, data: { key: value } }` |

文件下载接口（Excel 导出、字体文件）使用 `ResponseEntity<byte[]>` / `ResponseEntity<Resource>`，不走 Response 包装。

**前端 axios 拦截器**（`api/index.ts`）自动解包：检测到 `success` 字段时，成功则替换 `response.data` 为 `data` 字段值，失败则 reject `errMessage`。非 JSON 响应（Blob）直接透传。

### 前后端 Excel 数据流

`report-univer` 包提供双向快照能力，与后端 `report-engine-excel` 共享同一 JSON 结构（`ExcelWorkbook`）：

- **导出**：`sheetRef.getSnapshot()` → `extractSnapshot()` → `ExcelWorkbook` JSON → `POST /api/excel/generate` → `.xlsx` 下载
- **导入**：上传 `.xlsx` → `POST /api/excel/import` → `ExcelWorkbook` JSON → `sheetRef.loadSnapshot()` → 渲染到 Univer

### 关键类型系统

- `ExcelWorkbook` / `Workbook`：前后端共享的 Excel 模型，包含 sheets → cells / merges / rows / columns / loopBlocks
- `DataConfig` → `TableConfig[]` → `FieldConfig[]`：数据源配置，字段包含 dataType（STRING/NUMBER/DATE/DATETIME/BOOLEAN/JSON）和外键引用
- `ConditionRule`：条件规则，包含字段、运算符（12种 CompareOperator）、值
- `CalcMethod`：聚合计算方式（COUNT/SUM/AVG/MAX/MIN/COUNT_DISTINCT/COUNT_TRUE/COUNT_FALSE）
- 运算符和计算方式按数据类型智能过滤（`OPERATORS_BY_TYPE` / `CALC_METHODS_BY_TYPE`）

### Univer 集成注意事项

`UniverSheet` 组件裁剪了大量菜单项（冻结行列、权限保护、公式栏等），仅保留报表设计所需的最小功能集。循环块通过 Univer 原生 `setBorder()` API 绘制蓝色虚线边框，右键菜单通过 `univerAPI.createMenu()` 注册。

### 前端字体管理

`UniverSheet` 框架内置字体加载能力，与后端解耦：
- **`onFontRequest` 回调**：框架需要字体时调用，父组件负责 API 调用并返回 `FontItem[]`（含 `family` / `filename` / `url`）
- **内部自动处理**：localStorage 缓存（`report-fonts-vN`）→ .ttc 文件过滤 → `@font-face` CSS 注入 → `univerAPI.addFonts()` 注册
- **URL 不硬编码**：字体文件下载地址由后端 `FontItem.url` 提供，框架直接使用
- 内置 4 个通用字体（Arial / Times New Roman / Tahoma / Verdana）通过 `customFontFamily: { override: true }` 配置，自定义字体通过 API 动态注入

## 注意事项

- 后端默认数据库为 PostgreSQL（localhost:5432/report），运行 framework 测试前需确保数据库可用
- `report-engine-excel` 的测试不需要外部依赖（纯 POI 单元测试），可独立运行
- 前端库包使用 `bundle: false`（非打包模式），保留 tree-shaking 能力，消费方需要自行处理依赖
- 后端 `stype` 包名是 `style` 的拼写变体，重命名时需注意
- 使用 Lombok（`@Data` / `@Builder`），但部分类未加注解导致缺少 getter/setter
