# Report Engine

报表引擎框架，支持通过电子表格界面自定义配置报表模板，创建报表视图，并导出为 Excel 文件。

用户可以在类 Excel 的界面中设计报表布局，绑定数据源字段，配置条件规则和聚合计算方式，最终生成动态数据报表。

## 项目架构

```
后端 (Java 17 + Maven)
├── report-engine-excel       Excel 构建/解析 + 字体管理（纯 Java，Apache POI 封装）
├── report-engine-starter     Spring Boot 自动配置 + REST API
├── report-engine-framework   报表核心框架（数据源/组件/元数据）
└── report-engine-example     示例应用（仅启动类）

前端 (React 18 + TypeScript + pnpm monorepo)
├── packages/report-univer    Univer 电子表格 React 封装（字体管理、快照导入导出）
├── packages/report-engine    报表设计器组件库（三栏布局）
└── apps/app-pc               演示应用
```

### 核心技术栈

- **后端**：Spring Boot 3.5 / Apache POI 5.x / Jackson
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

### 待开发

- [ ] **数据源管理**
  - 数据库连接配置与测试
  - 表/字段/主键/外键元数据扫描
  - 数据源树形浏览面板（前端 antd Tree）

- [ ] **报表配置逻辑**
  - 字段拖入单元格绑定
  - X/Y 轴条件规则配置（12 种运算符，按数据类型智能过滤）
  - 聚合计算方式（COUNT/SUM/AVG/MAX/MIN 等）
  - 单元格属性模板系统

- [ ] **报表数据呈现**
  - 数据源查询执行（SQL 参数化 + 条件拼接）
  - 循环块数据展开（主从表嵌套渲染）
  - 条件格式与动态样式

- [ ] **报表数据导出**
  - 报表模板 → 填充数据 → 生成 .xlsx 下载
  - 多 Sheet 导出
  - 打印预览与分页

## 快速开始

### 后端

```bash
# 编译全部模块
./mvnw clean compile

# 运行 Excel 模块测试（纯 POI，无外部依赖）
./mvnw test -pl report-engine-excel

# 启动示例应用（端口 8080）
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

## License

Apache 2.0
