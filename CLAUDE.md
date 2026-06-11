# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Report Engine 是一个报表引擎框架，支持用户通过电子表格界面设计报表模板，配置数据源、条件规则和计算方式，最终生成数据报表。前后端一体化仓库管理，处于早期开发阶段 (v0.0.1)。

## 构建与运行命令

### 后端 (Java 17 + Maven)

```bash
# 编译全部模块（framework + example）
./mvnw clean compile

# 运行 framework 模块测试（需要本地 PostgreSQL: localhost:5432/report）
./mvnw test -pl report-engine-framework

# 运行单个测试类
./mvnw test -pl report-engine-framework -Dtest=DataServiceTest

# 仅编译 travis profile（framework 模块 + JaCoCo 覆盖率）
./mvnw clean test -P travis

# 发布到 Maven Central（需要 GPG 签名）
./mvnw clean deploy -P ossrh

# 启动 example 应用（端口 8090，目前为空壳）
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
├── report-engine-framework/     # 后端核心框架（可发布到 Maven Central）
├── report-engine-example/       # 后端示例 Spring Boot 应用
└── report-frontend/             # 前端 pnpm monorepo
    ├── packages/report-univer/  # @coding-report/report-univer — Univer 电子表格 React 封装
    ├── packages/report-engine/  # @coding-report/report-engine — 报表设计器核心组件库
    └── apps/app-pc/             # @report-example/app-pc — 演示应用
```

## 后端架构

### 模块关系

`report-engine-framework` 是一个 Spring Boot 自动配置库（starter），不依赖任何 Web 框架运行时。`report-engine-example` 是可启动的 Spring Boot 应用（目前仅有启动类，尚未引入 framework 依赖）。

### 核心包结构 (`com.codingapi.report`)

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
- 电子表格引擎：Univer 0.25（`@univerjs/presets` + `UniverSheetsCorePreset`）
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

### 关键类型系统

- `DataConfig` → `TableConfig[]` → `FieldConfig[]`：数据源配置，字段包含 dataType（STRING/NUMBER/DATE/DATETIME/BOOLEAN/JSON）和外键引用
- `ConditionRule`：条件规则，包含字段、运算符（12种 CompareOperator）、值
- `CalcMethod`：聚合计算方式（COUNT/SUM/AVG/MAX/MIN/COUNT_DISTINCT/COUNT_TRUE/COUNT_FALSE）
- 运算符和计算方式按数据类型智能过滤（`OPERATORS_BY_TYPE` / `CALC_METHODS_BY_TYPE`）

### Univer 集成注意事项

`UniverSheet` 组件裁剪了大量菜单项（冻结行列、权限保护、公式栏等），仅保留报表设计所需的最小功能集。循环块通过 Univer 原生 `setBorder()` API 绘制蓝色虚线边框，右键菜单通过 `univerAPI.createMenu()` 注册。

## 注意事项

- 后端默认数据库为 PostgreSQL（localhost:5432/report），运行 framework 测试前需确保数据库可用
- `report-engine-example` 目前未依赖 `report-engine-framework`，example 模块需要补充实际示例代码
- 前端库包使用 `bundle: false`（非打包模式），保留 tree-shaking 能力，消费方需要自行处理依赖
- 后端 `stype` 包名是 `style` 的拼写变体，重命名时需注意
- 使用 Lombok（`@Data` / `@Builder`），但部分类未加注解导致缺少 getter/setter
