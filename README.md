# Report Engine

报表引擎框架，支持通过电子表格界面自定义配置报表模板，创建报表视图，并导出为 Excel 文件。

用户可以在类 Excel 的界面中设计报表布局，绑定数据源字段，配置条件规则和聚合计算方式，最终生成动态数据报表。

## 项目架构

```
后端 (Java 17 + Maven)
├── report-engine-framework   报表核心框架（声明式数据模型 + 内存渲染引擎）
├── report-engine-excel       Excel 构建/解析 + 字体管理（Apache POI 封装）
├── report-engine-starter     Spring Boot 自动配置 + REST API
└── report-engine-example     示例应用（仅启动类）

前端 (React 18 + TypeScript + pnpm monorepo)
├── packages/report-univer    Univer 电子表格 React 封装（字体管理、快照导入导出）
├── packages/report-engine    报表设计器组件库（三栏布局）
└── apps/app-pc               演示应用
```

### 核心技术栈

- **后端**：Spring Boot 3.5 / Apache POI 5.x / Jackson / Lombok
- **前端**：React 18 / Univer 0.25 / Ant Design 6 / Rslib + Rsbuild
- **数据契约**：前后端共享 `ExcelWorkbook` JSON 结构（Java POJO ↔ TypeScript 类型一一对应）

## 当前进度

### 已完成

- [x] **Excel 组件封装**（`report-univer`）：Univer 插件模式集成，裁剪为报表设计最小功能集
- [x] **Excel 双向转换**（`report-engine-excel`）：JSON ↔ .xlsx 导入导出，支持完整样式（字体/对齐/边框/填充/富文本）
- [x] **字体管理系统**：
  - 后端：双目录扫描（内置 + 自定义）、文件名前缀排序、JVM 注册
  - 前端：`onFontRequest` 回调机制、localStorage 缓存、@font-face 动态注入
- [x] **Spring Boot Starter**（`report-engine-starter`）：自动装配 FontRegistry、FontController、ExcelController
- [x] **API 响应标准化**：统一使用 `SingleResponse` / `MultiResponse` 包装，前端 axios 拦截器自动解包
- [x] **报表设计器布局**（`report-engine`）：三栏式布局（数据源 / 表格 / 属性），可拖拽调整宽度
- [x] **循环块管理**：右键菜单创建/删除，蓝色虚线高亮，属性绑定
- [x] **单元格操作句柄**（`CellHandle`）：样式读写、值设置、富文本支持
- [x] **声明式数据模型**（`report-engine-framework`）：
  - 源模型：DataSource / Dataset / Field / Relationship / Query / UnionMember
  - 网格模型：CellBinding（TextCell / FieldCell）、Condition、Aggregation、LoopBlock、SummaryRow
  - 参数模型：Parameter / ParamSource（External/Cell/Constant）/ ValueRef（Literal/Param/LoopField）
- [x] **内存渲染引擎**：ReportRenderer 支持 7 种报表场景（列表/合并/多级统计/循环块/主从/小计/UNION）
- [x] **跨数据源 JOIN**：所有计算在 Java 内存完成，支持异构数据源关联

### 待开发

- [ ] **表达式抽象**：统一 ValueRef / ParamSource / 文本插值的表达式类型体系
- [ ] **前端参数绑定**：ConditionRule 支持 Param/LoopField 值引用（目前仅支持字面量）
- [ ] **数据源管理面板**：数据库连接配置、元数据扫描、前端数据源树
- [ ] **报表配置持久化**：DataModel / Report 模型的存储与加载
- [ ] **报表数据呈现**：前端实时预览填充数据后的报表效果
- [ ] **报表数据导出**：报表模板 → 填充数据 → 生成 .xlsx 下载

## 快速开始

### 后端

```bash
# 编译全部模块
./mvnw clean compile

# 运行 framework 模块测试（纯内存，无外部依赖）
./mvnw test -pl report-engine-framework

# 运行 Excel 模块测试（纯 POI，无外部依赖）
./mvnw test -pl report-engine-excel

# 启动示例应用（端口 8090）
./mvnw spring-boot:run -pl report-engine-example
```

### 前端

```bash
cd report-frontend

# 安装依赖
pnpm install

# 构建库包（report-univer → report-engine）
pnpm build

# 启动演示应用
pnpm dev:app-pc
```

### 自定义字体

在 `application.properties` 中配置字体目录：

```properties
report.fonts.dir=/path/to/custom/fonts
```

字体文件命名约定：数字前缀控制排序（如 `01_微软雅黑.ttf`、`02_宋体.ttf`），无编号按文件名自然序。

## 字体版权声明

本项目内置的字体文件（Arial、Times New Roman、Tahoma、Verdana 等）仅用于开发调试目的，其版权归原作者/公司所有（Monotype、Microsoft 等）。

如果您是字体版权方，认为本项目中的字体使用侵犯了您的权益，请联系我们进行移除：

- 邮箱：wangliang@codingapi.com
- 或通过 GitHub Issues 提交移除请求

收到合理请求后，我们将在第一时间删除相关字体文件。

**建议生产环境使用方式**：
- 使用操作系统已授权的字体（系统自带）
- 使用开源字体替代（如 Google Liberation 字体家族，Apache 2.0 开源）
- 自行采购商用字体授权后放入自定义字体目录

## License

Apache 2.0
