# QWEN.md

## Project Overview

A React component library wrapping [Univer](https://univer.ai) (open-source spreadsheet engine) as an embeddable report/spreadsheet component. Published as `@coding-report/report-engine`.

**Monorepo structure (pnpm workspaces):**
- `packages/report-engine/` — the publishable library (`@coding-report/report-engine`)
- `apps/app-pc/` — private demo app (`@report-example/app-pc`) that depends on the library via `workspace:*`

**Tech stack:** TypeScript 5.9, React 18, Univer v0.24 (zh-CN locale), Rslib (library build, ESM no-bundle with .d.ts), Rsbuild (demo app), pnpm 10.

## Commands

Run from the repo root. All commands delegate to sub-packages via `pnpm -F`.

| Command | Purpose |
|---|---|
| `pnpm run build` | Build the library for production (Rslib, ESM + d.ts) |
| `pnpm run watch:report-engine` | Build the library in watch mode |
| `pnpm run dev:app-pc` | Start the demo app dev server (Rsbuild, auto-opens browser) |
| `pnpm run push` | Build + publish library to npm (`pnpm publish --access public`) |
| `pnpm -F @coding-report/report-engine test` | Run library tests with Rstest |

**Typical development workflow:** run `pnpm run watch:report-engine` in one terminal and `pnpm run dev:app-pc` in another. The demo app consumes the library via workspace link, so library rebuilds are picked up on next HMR.

## Architecture

### The library: `packages/report-engine/src/`

Entry point: `index.ts` re-exports from `report-engine.tsx`. Top-level `report-engine.tsx` is a **thin orchestrator** — it imports `Group` from `react-resizable-panels`, the `Panel` layout primitive, and the three domain panel components, then wires them into the three-column layout. It should never contain UI logic.

```
src/
├── index.ts                   ← barrel export
├── report-engine.tsx          ← layout orchestrator (Group + Panels)
├── index.css                  ← all component styles (BEM: .report-engine__*)
├── types.ts                   ← re-exports types from datasource/types + ReportEngineProps
└── components/
    ├── datasource/
    │   ├── index.tsx          ← DataSourcePanel (left column: antd Tree of tables/fields)
    │   └── types.ts           ← DataType, FieldConfig, TableConfig, DataConfig, ForeignKey
    ├── engine/
    │   ├── index.tsx          ← SheetPanel (middle column: Univer + context menus)
    │   └── univer/
    │       ├── index.tsx      ← UniverSheet (createUniver init/cleanup)
    │       └── type.ts        ← UniverSheetProps interface
    ├── layout/panel.tsx       ← Panel primitive (ResizablePanel + Separator + antd Button)
    └── properties/index.tsx   ← PropertyPanel (right column: placeholder, returns null)
```

### Three-column layout

```
┌──────────────┬──────────────────────────────────┬──────────────┐
│ 数据配置      │          UniverSheet             │  属性设置     │
│ DataSource   │          SheetPanel              │  Property    │
│ (collapsible)│          (fixed, center)         │  (collapsible)│
└──────────────┴──────────────────────────────────┴──────────────┘
```

Left/right panels: 15% default, min 200px, collapsible. Center panel: 70%, not collapsible.

### The `Panel` component (`components/layout/panel.tsx`)

The single layout primitive. Wraps `react-resizable-panels` `ResizablePanel` + `Separator` and adds title bar, collapsible toggle (with antd `MenuFoldOutlined`/`MenuUnfoldOutlined` icons), position-aware borders. Props: `title`, `position` (left|center|right), `defaultSize`, `minSize`, `withSeparator`, `collapsible`, `collapsedSize`, `children`.

**Rule:** the orchestrator (`report-engine.tsx`) imports only `Group` from `react-resizable-panels` directly. All resize/splitter UI is delegated to `Panel`. Content panel components in `components/{datasource,engine,properties}/` must not import `react-resizable-panels`.

### Key types (`components/datasource/types.ts`)

- **`DataType` enum**: `STRING`, `NUMBER`, `DATE`, `DATETIME`, `BOOLEAN`, `JSON` — simplified business types, not raw DB types
- **`ForeignKey`**: `{ referenceTable, referenceField }` — no relation type, just pointers
- **`FieldConfig`**: `{ name, alias?, dataType, isPrimary?, foreignKey?, description? }`
- **`TableConfig`**: `{ id, name, alias?, fields[], description? }`
- **`DataConfig`**: `{ name, tables[] }` — root config object
- **`ReportEngineProps`**: `{ dataConfig?: DataConfig }`

### Dependencies

- **Runtime:** `@univerjs/preset-sheets-core`, `@univerjs/presets`, `react-resizable-panels`, `rxjs`
- **Peer (consumer must provide):** `react >=18`, `react-dom >=18`, `antd >=5`, `@ant-design/icons >=5`
- UI components use antd (Button, Tree) and @ant-design/icons. Do **not** add antd to `dependencies` — it must remain a peerDependency.

### Build configuration

- **`rslib.config.ts`**: `bundle: false`, `dts: true`, `format: 'esm'`. Path alias `@/` → `src/`. Output: `dist/` (ESM + .d.ts, published to npm).
- **`apps/app-pc/rsbuild.config.ts`**: `pluginReact`, path alias `@/` → `src/`. Minimal config.

### Demo app (`apps/app-pc/`)

Minimal Rsbuild + React app that renders `<ReportEngine dataConfig={mockDataConfig} />` on its home page. `data/mock-data.ts` provides sample data with 3 tables (sys_user, sys_department, biz_order) demonstrating all DataType variants, primary keys, foreign keys, and self-referencing relationships.

### Testing

Test framework: Rstest (`@rstest/core` + `@rstest/adapter-rslib`) with `happy-dom` and `@testing-library/react`. Run via `pnpm -F @coding-report/report-engine test`. No test files exist yet (`*.test.{ts,tsx}`).

## Development Conventions

### Workflow

1. **Plan first** — 收到开发任务后，必须先输出实施计划（修改哪些文件、做什么改动），不得直接开始编码。
2. **Human review** — 计划需经人工核实确认后方可执行。
3. **Execute** — 确认后再进行实际编辑，严格按照计划执行。

### Component rules

- **No UI in top-level files** — all components live under `components/`. Top-level files (`report-engine.tsx`) only orchestrate and re-export.
- **Domain directories** — each feature area has its own subdirectory (`datasource/`, `engine/`, `layout/`, `properties/`).
- **Single responsibility** — each component file has one job.
- **New features** — create a component file in the appropriate domain directory, then export from the entry point.

### Naming

| Type | Convention | Example |
|---|---|---|
| File names | kebab-case | `report-engine.tsx`, `data-source-panel.tsx` |
| Component names | PascalCase | `ReportEngine`, `DataSourcePanel` |
| Directory names | kebab-case | `components/engine/`, `components/layout/` |
| CSS classes | BEM with `.report-engine` prefix | `.report-engine__panel--left` |

### Coding principles

1. **Think before coding** — state assumptions explicitly; ask if unclear.
2. **Simplicity first** — minimum code that solves the problem. No speculative features.
3. **Surgical changes** — touch only what you must. Match existing style. Don't "improve" adjacent code.
4. **Goal-driven execution** — define verifiable success criteria and loop until verified.

## Current Status

**Done:** three-column layout, draggable panel widths, left/right panel collapse, Univer Ribbon hidden (`ribbonType: 'collapsed'`), data config tree display with type icons and PK/FK indicators, cell/row/column context menus.

**TODO:** DataSourcePanel interaction (drag fields into cells?), PropertyPanel implementation, more spreadsheet feature extensions.
