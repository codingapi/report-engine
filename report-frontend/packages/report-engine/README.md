# @coding-report/report-engine

报表设计器组件库（React 18 + TypeScript + Ant Design 6 + Univer 0.25）。纯 UI 库，不直接调后端 API，渲染函数由消费方通过 `renderService` 注入。

## 核心导出

- **`ReportEngine`** — 三栏式报表设计器（左数据模型 / 中电子表格 / 右属性面板）。
- **`ReportPreview`** — 预览能力组件（参数弹窗 → 渲染 → 预览抽屉 → 反查 → 抽屉内导出）。
- **`useReportPreview`** — 预览流程 hook（逻辑/状态）。
- **`DataModelListPage`** — 数据模型管理页（列表 + 全屏抽屉内置 `DataModelDesigner`，`designerService` 注入加载/保存）。
- **`DataModelDesigner`** — 数据模型设计器（数据集 / 数据合集 / 关系 三 tab）。
- 领域类型：`CellBinding` / `LoopBlock` / `SummaryRow` / `ParamDTO` / `ReportValue` / `ReportDTO` / `RenderConfig` / `RenderService` 等（`ReportDTO`/`ParamDTO` 与后端同名对齐）。

## ReportEngine

顶部按钮布局（宽度统一 112px）：

```
[customActions] | 导入模板 | 循环块 | 报表预览 | 导出报表 | 保存报表 | [extraActions]
```

- 默认按钮受 `enableImport` / `enableLoopBlock` / `enablePreview` / `enableExport` / `enableSave`（默认 `true`）控制，且受 `onImport` / `renderService` / `onSaveReport` 前置条件约束。
- `customActions?` — 渲染在默认组左侧（有内容时加竖线分隔）。
- `extraActions?` — 渲染在默认组右侧（保存报表右侧）。
- `renderService?` — 注入 `{ preview, export, drill }`（report-api 的 `previewReport` / `renderReport` / `drillReport`），启用内置预览/导出全流程。

## ReportPreview

设计器与独立预览页共用：

- 声明式：`config` 引用变化即触发预览。
- 命令式：通过 ref 调 `exportXlsx(config)` 直接导出。
- `onClose?` — 抽屉关闭回调（设计器不传，预览页可借此返回上一页）。

## 使用

```tsx
import { ReportEngine, ReportPreview } from '@coding-report/report-engine';
import { previewReport, renderReport, drillReport } from '@coding-report/report-api';
```

库包使用 `bundle: false`（非打包模式），保留 tree-shaking 能力。

## License

Apache 2.0
