# Report Engine Frontend

基于 [Univer](https://univer.ai) 的 React 电子表格/报表组件库。

## 项目结构

```
report-frontend/
├── packages/
│   ├── report-univer/     # @coding-report/report-univer — Univer 封装层
│   ├── report-api/        # @coding-report/report-api — 后端 API 客户端
│   └── report-engine/     # @coding-report/report-engine — 报表设计器 + 数据源管理组件库
└── apps/
    └── app-pc/            # @report-example/app-pc — 演示应用
```

### 包依赖关系

```
app-pc → report-engine / report-api → report-univer → @univerjs/* v0.25
```

构建顺序：report-univer → report-api → report-engine（`pnpm build` 脚本已处理）。

## 快速开始

```bash
pnpm install        # 安装依赖
pnpm build          # 构建所有库包
pnpm dev:app-pc     # 启动演示应用
```

### 开发模式

```bash
pnpm watch:report-univer   # 库包变更后自动重编译（终端 1）
pnpm dev:app-pc            # 启动应用（终端 2）
```

## 核心包说明

### @coding-report/report-univer

Univer 电子表格的 React 封装层，提供：

- `UniverSheet` 组件：声明式 props + 命令式 ref（`UniverSheetHandle`）
- 快照导入/导出：`extractSnapshot()` / `renderSnapshot()` 与后端 `ExcelWorkbook` JSON 互通
- 字体管理：`onFontRequest` 回调 + localStorage 缓存 + @font-face 注入
- 循环块高亮、右键菜单、字段拖拽

库包使用 `bundle: false`（非打包模式），保留 tree-shaking 能力。

### @coding-report/report-api

后端 API 客户端：axios 实例（`baseURL: '/api'`）+ 响应拦截器自动解包 `SingleResponse` / `MultiResponse`。暴露 `saveReportConfig` / `loadReportConfig` / `deleteReportConfig` / `listReportConfigs(current,pageSize)` / `listDataModels` / `renderReport` / `previewReport` / `drillReport` / `exportExcel` / `importExcel` / `fetchFonts` 等。

### @coding-report/report-engine

报表设计器组件库（纯 UI，不直接调 API）：

- **`ReportEngine`**：三栏式布局（左数据模型 / 中电子表格 / 右属性面板）。顶部按钮可配置：默认组（导入模板/循环块/报表预览/导出报表/保存报表）受 `enableImport`/`enableLoopBlock`/`enablePreview`/`enableExport`/`enableSave` 控制；`customActions`（左，加竖线分隔）+ `extraActions`（右）注入自定义按钮。预览/导出通过 `renderService` prop 注入 report-api 函数启用。
- **`ReportPreview`**：预览能力组件（参数弹窗 → 渲染 → 预览抽屉 → 反查 → 抽屉内导出），设计器与独立预览页共用。声明式 `config`（引用变化触发预览）+ `onClose` 回调 + ref `exportXlsx` 命令式导出。
- **`useReportPreview`** hook：预览流程逻辑/状态（纯逻辑，JSX 在组件层渲染）。
- **数据源管理组件**（原 `report-datasource` 已并入本包）：`ConnectionForm`（连接配置）/ `DatasetManager`（数据集增删）/ `RelationEditor`（关系编辑）/ `ExploreTree`（表/列探查）+ `useDatasource`/`useExplore` hook，经 `DatasourceService` prop 注入 report-api 实现。数据源类型对齐后端：`DB`/`EXCEL`/`CSV`。
- **数据模型管理组件**：`DataModelListPage`（列表 + 全屏抽屉内置设计器，`designerService` 注入加载/保存）、`DataModelDesigner`（数据集 / 数据合集 / 关系 三 tab，`forwardRef` 暴露 `save` + `onModelChange`）。

### app-pc

演示应用（基于 Rsbuild）。路由：`/`（首页）+ `/datasource-types`（驱动管理）+ `/datasources`（数据源管理）+ `/datamodels`（数据模型管理）+ `/reports`（报表管理，antd Table 分页 + 新建/编辑/预览/删除）+ `/engine`（`AppReport` 设计器）+ `/preview`（`AppPreview` 独立预览页）。菜单 5 项：首页 / 驱动管理 / 数据源管理 / 数据模型管理 / 报表管理。

## 技术栈

- React 18 + TypeScript 5.9 + Ant Design 6
- Univer 0.25（电子表格引擎，插件模式）
- Rslib（库包构建）+ Rsbuild（应用构建）
- Rstest + @testing-library/react + happy-dom（测试）
- pnpm 10 workspaces

## License

Apache 2.0
