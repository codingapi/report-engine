# Report Engine

报表引擎框架，支持通过电子表格界面自定义配置报表模板，创建报表视图，并导出为 Excel 文件。

用户可以在类 Excel 的界面中设计报表布局，绑定数据源字段，配置条件规则和聚合计算方式，最终生成动态数据报表。

## 项目架构

```
后端 (Java 17 + Maven)
├── report-engine-framework   报表核心框架（声明式数据模型 + 内存渲染引擎 + 存储抽象）
├── report-engine-excel       Excel 构建/解析 + 字体管理（Apache POI 封装）
├── report-engine-starter     Spring Boot 自动配置 + 全部通用 REST API + DTO 转换适配
└── report-engine-example     示例应用（数据集配置 + 示例报表预存）

前端 (React 18 + TypeScript + pnpm monorepo)
├── packages/report-univer    Univer 电子表格 React 封装（字体管理、快照导入导出）
├── packages/report-api       后端 API 客户端（axios 实例 + SingleResponse/MultiResponse 自动解包）
├── packages/report-engine    报表设计器组件库（三栏布局，纯 UI 不直接调 API）
└── apps/app-pc               演示应用
```

### 核心技术栈

- **后端**：Spring Boot 3.5 / Apache POI 5.x / Jackson / Lombok
- **前端**：React 18 / Univer 0.25 / Ant Design 6 / Rslib + Rsbuild
- **数据契约**：前后端共享 `ExcelWorkbook` JSON 结构（Java POJO ↔ TypeScript 类型一一对应）

### 框架扩展点（report-engine-framework）

核心框架纯 Java、不依赖 Spring，可单独发布；模板层（视觉呈现）与语义层（取数/计算）完全分离。四类能力统一用 `supports()` + 注册表范式扩展，新增无需改动调用方，未注册者**显式抛异常**：

| 扩展什么 | 怎么扩展 |
|---|---|
| 新数据源类型（DB/API/…） | 实现 `DataExtractor`，注册到 `ReportRenderer` 的 extractors 列表 |
| 新比较算子（REGEX/BETWEEN…） | 实现 `ConditionPredicate`，登记到注册表 |
| 新聚合方式（COUNT_TRUE…） | 实现 `Aggregator`，登记到注册表 |
| 新表达式函数（round/concat/if…） | 实现 `ValueFunction`，登记到注册表 |

## 当前进度

### 已完成

- [x] **Excel 组件封装**（`report-univer`）：Univer 插件模式集成，裁剪为报表设计最小功能集
- [x] **Excel 双向转换**（`report-engine-excel`）：JSON ↔ .xlsx 导入导出，支持完整样式（字体/对齐/边框/填充/富文本）
- [x] **字体管理系统**：
  - 后端：双目录扫描（内置 + 自定义）、文件名前缀排序、JVM 注册
  - 前端：`onFontRequest` 回调机制、localStorage 缓存、@font-face 动态注入
- [x] **Spring Boot Starter**（`report-engine-starter`）：自动装配 FontRegistry + 全部通用 REST API（渲染 / 数据集 / 公式目录 / 报表配置 CRUD / 数据模型列表 / 字体 / Excel）；`ReportRepository` 接口（`save/find/page(PageQuery):PageResult/delete`）+ 领域实体 `core.Report` / `data.datamodel.DataModel`（自带 `toDTO/fromDTO`）均在 **framework**（零 Spring 依赖，分页用 framework 纯类型 `PageQuery`/`PageResult`），starter 仅在 Controller 边界做 `SearchRequest↔PageQuery` / `PageResult↔MultiResponse` 适配，`@ConditionalOnMissingBean` 允许使用方覆盖实现
- [x] **API 响应标准化**：统一使用 `SingleResponse` / `MultiResponse` 包装，前端 axios 拦截器自动解包
- [x] **报表设计器布局**（`report-engine`）：三栏式布局（数据模型 / 表格 / 属性），可拖拽调整宽度，面板可收缩
- [x] **循环块管理**：右键菜单创建/删除，Tab 化多循环块管理，蓝色虚线高亮，循环字段级联选择
- [x] **单元格操作句柄**（`CellHandle`）：样式读写、值设置、富文本支持
- [x] **声明式数据模型**（`report-engine-framework`）：
  - 数据域：DataSource（聚合根，持有 List<Dataset>；`DataSourceType` **sealed interface**：CSV/EXCEL/DB 三实现）/ Dataset（sealed → TableDataset 聚合 DataSource / UnionDataset）/ Field / Relationship
  - 算子域：Aggregation（SUM/COUNT/AVG/MAX/MIN/COUNT_DISTINCT）/ Condition（已实现 13 种比较算子：EQ/NE/GT/GE/LT/LE/CONTAINS/NOT_CONTAINS/IN/NOT_IN/IS_NULL/IS_NOT_NULL/BETWEEN + SPI 扩展）
  - 表达式域：Value（sealed，8 种节点：Literal / FieldValue / ParamValue / LoopFieldValue / NameRef / Template / Aggregate / FunctionCall）/ ExpressionEngine 注册表分发 / ValueFunction SPI
  - 参数域：ParamSource（External / Cell / Constant）
  - 渲染域：CellBinding（值层 Value + 控制层 expansion/merge/conditions）/ LoopBlock / SummaryRow
- [x] **表达式引擎**：统一 `${...}` 文本语法（`Templates.parse()`），支持字段引用、聚合函数、文本插值、函数调用；内置函数 format / date / round / concat / if（`ValueFunction` SPI 可扩展）；`splitArgs` 支持嵌套括号与字符串字面量
- [x] **内存渲染引擎**：ReportRenderer 支持 7 种报表场景（列表/合并/多级统计/循环块/主从/小计/UNION）+ 独立数据带并列渲染；汇总支持交叉区间作用域（并列报表各带独立汇总互不串扰）
- [x] **双向汇总（纵向 + 横向）**：`SummaryRow` 以 `Axis` 抽象统一两轴——纵向汇总在数据带**下方追加合计行**、横向汇总在数据带**右侧追加合计列**（互为转置，共用 `renderBand`/`summaryOut`）。坐标按轴表达（`mainPos` 主轴位置 / `crossFrom~crossTo` 交叉区间 / `crossPos` 交叉坐标）；前端右键按选区形状自动判轴（横选→纵向合计行、竖选→横向合计列）
- [x] **横向扩展与交叉表**（`Expansion.HORIZONTAL`）：引擎以 `Axis` 抽象统一纵/横两轴——纵向带（一记录一行）的转置即横向带（一记录一列，向右铺开 + 列位移/横向 GROUP 合并）；进一步支持 **VERTICAL×HORIZONTAL 交叉表（矩阵/透视）**：行维 × 列维 → 交叉格聚合，并按"紧邻交叉格"几何约定自动补出行合计/列合计/总计（零持久化契约变更，现有设计器直接可配）
- [x] **独立纵向带**（`CellBinding.independent`）：显式配置某列从自身声明行独立向下展开（交错/错位排版），默认仍按"一条记录一行"对齐同源列
- [x] **样式/布局适配**：模板静态内容（标题/页脚）随带扩展下移、汇总行继承模板样式、合并区边框铺满整个区域、模板行高/列宽随渲染带出
- [x] **跨数据源 JOIN**：所有计算在 Java 内存完成，支持异构数据源关联；JOIN 类型 INNER/LEFT/RIGHT/FULL（hash join，LEFT/RIGHT 保留侧相对 join 参数位置，无匹配侧补 null）
- [x] **数据模型面板**（`DataModelPanel`）：两 tab 布局（数据集 / 数据关系），始终显示数量徽标
- [x] **数据集树**（`DatasetTree`）：数据源类型彩色标签（DB/EXCEL/CSV）、字段拖拽、字段级关系双侧标注（→ FK / ← PK）
- [x] **数据关系与分组**：上半区关系列表 + 下半区数据分组树（union-find 连通分量，仅展示有关系的数据集）
- [x] **表达式构建器**（`ExpressionBuilder`）：计算器式统一值编辑，支持字段插入、聚合、函数调用、模板插值，实时预览
- [x] **报表领域实体化**：`core.Report` 领域实体（framework，含 id/name/dataModelId/createTime/updateTime/cellBindings/loopBlocks/summaries/parameters/template + 运行时 dataModel 引用），DTO record 在 framework `dto.report.*`（前端 JSON 契约），经 `Report.toDTO()`/`fromDTO()` 互转；仓库以领域对象存取，`ReportRepository.page(PageQuery):PageResult<Report>` 分页（接口在 framework）
- [x] **报表配置持久化**：`ReportConfigController`（starter）保存/加载/分页列表/删除 API，数据模型随配置加载附带返回；example 用 `ReportConfigBuilder` 链式预存 10 个示例报表（含交叉表「区域季度销售交叉表」、横向汇总「商品横向汇总表」，写死稳定 id，重启不变）
- [x] **报表渲染导出**：`POST /api/report/render`（starter）→ 填充数据的 .xlsx 下载，DTO record（framework `dto.report.*`）+ framework `core.RenderDtoConverter` 匹配前端 JSON 格式
- [x] **网页预览能力**：`ReportPreview` 组件（report-engine，参数弹窗→渲染→预览抽屉→反查→抽屉内导出），设计器与独立预览页共用；报表参数运行时输入表单（必填参数弹窗）
- [x] **管理界面**：app-pc 五个菜单（首页 / 驱动管理 / 数据源管理 / 数据模型管理 / 报表管理）；数据源管理为 4 步向导（类型 / 配置 / 解析 / 预览，解析不落库、保存才落库）；数据模型管理为列表 + 全屏抽屉设计器（`DataModelListPage` 内置 `DataModelDesigner`，三 tab：数据集 / 数据合集 / 关系），含**发布状态机**（草稿可修改/发布，已发布屏蔽修改、可转草稿；仅已发布模型可被报表选用）；报表管理页 antd Table 分页、新建/编辑/预览/删除，引用未发布模型的报表标「未发布」并禁用编辑/预览
- [x] **动态报表标题**：标题栏显示当前报表名称（从配置加载），保存时同步更新

### 待开发

#### 引擎能力

- [ ] **交叉表的汇总归一**：横向带汇总已支持（见已完成）；交叉表的行/列/总合计仍由专用的"紧邻交叉格"几何约定补出，尚未归一到 `SummaryRow` 机制，维度列也暂不做表头合并
- [ ] **交叉表设计向导**：交叉表当前靠几何摆位（行维纵向分组 × 列维横向分组 + 交点聚合）配置，前端尚无引导式向导与方向可视化提示

#### 数据与存储

- [ ] **持久化存储实现**：`ReportRepository` 接口齐全，starter 不提供默认实现（`@ConditionalOnMissingBean` 交使用方），example 为 `InMemoryReportRepository`（重启丢失）。生产接入需自实现 JPA/文件等持久化

#### 产品演进

- [ ] **报表权限与共享**：报表配置的权限控制、多人协作、版本管理
- [ ] **打印与分页**：报表分页设置、打印预览、页眉页脚配置

#### 已知限制

- [ ] **条件面板未按类型过滤算子**：`CompareOperator` 注释定义了按 `DataType` 过滤的可用算子表，但前端 `condition-modal` 当前全量展示 13 个算子（后端均已支持），未按字段类型收敛

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

# 构建库包（report-univer → report-api → report-engine）+ app-pc
pnpm build

# 跑库包单测（report-univer + report-engine，rstest）
pnpm test

# 启动演示应用
pnpm dev:app-pc
```

### 代码格式化

前后端格式化能力已接入，手动触发：

```bash
./scripts/format.sh              # 全量（前端 prettier + 后端 spotless）
./scripts/format.sh frontend     # 仅前端：prettier（单引号/分号/2空格，配置见 report-frontend/.prettierrc）
./scripts/format.sh backend      # 仅后端：./mvnw spotless:apply（AOSP / 4 空格）
```

### 自定义字体

在 `application.properties` 中配置字体目录：

```properties
codingapi.report.font.dir=/path/to/custom/fonts
```

字体文件命名约定：数字前缀控制排序（如 `01_微软雅黑.ttf`、`02_宋体.ttf`），无编号按文件名自然序。

## 字体版权声明

本项目内置的字体文件（Arial、Times New Roman、Tahoma、Verdana 等）仅用于开发调试目的，其版权归原作者/公司所有（Monotype、Microsoft 等）。

如果您是字体版权方，认为本项目中的字体使用侵犯了您的权益，请联系我们进行移除：

- 邮箱：1024lorne@gmail.com
- 或通过 GitHub Issues 提交移除请求

收到合理请求后，我们将在第一时间删除相关字体文件。

**建议生产环境使用方式**：
- 使用操作系统已授权的字体（系统自带）
- 使用开源字体替代（如 Google Liberation 字体家族，Apache 2.0 开源）
- 自行采购商用字体授权后放入自定义字体目录

## License

Apache 2.0
