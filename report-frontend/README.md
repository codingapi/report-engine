# Report Engine Frontend

基于 [Univer](https://univer.ai) 的 React 电子表格/报表组件库。

## 项目结构

```
report-frontend/
├── packages/
│   ├── report-univer/     # @coding-report/report-univer — Univer 封装层
│   └── report-engine/     # @coding-report/report-engine — 报表设计器组件库
└── apps/
    └── app-pc/            # @report-example/app-pc — 演示应用
```

### 包依赖关系

```
app-pc → report-engine → report-univer → @univerjs/* v0.25
```

构建顺序：report-univer → report-engine（`pnpm build` 脚本已处理）。

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

### @coding-report/report-engine

报表设计器组件库，三栏式布局：

- **左面板**：数据源树形浏览
- **中面板**：Univer 电子表格（报表模板设计）
- **右面板**：单元格属性配置（条件规则 + 计算方式）

### app-pc

演示应用，基于 Rsbuild 构建，用于验证和展示组件库能力。

## 技术栈

- React 18 + TypeScript 5.9 + Ant Design 6
- Univer 0.25（电子表格引擎，插件模式）
- Rslib（库包构建）+ Rsbuild（应用构建）
- Rstest + @testing-library/react + happy-dom（测试）
- pnpm 10 workspaces

## License

Apache 2.0
