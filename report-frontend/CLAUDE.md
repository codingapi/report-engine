# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
└── components/
    ├── datasource/index.tsx   ← DataSourcePanel (left column content)
    ├── engine/
    │   ├── index.tsx          ← SheetPanel (middle column: Univer spreadsheet)
    │   └── univer/            ← UniverSheet wrapper + types (Univer init/cleanup)
    ├── layout/panel.tsx       ← Panel primitive (ResizablePanel + Separator + antd Button)
    └── properties/index.tsx   ← PropertyPanel (right column content)
```

### The `Panel` component (`components/layout/panel.tsx`)

The single layout primitive. Wraps `react-resizable-panels` `ResizablePanel` + `Separator` and adds title bar, collapsible toggle (with antd `MenuFoldOutlined`/`MenuUnfoldOutlined` icons), position-aware borders. Props: `title`, `position` (left|center|right), `defaultSize`, `minSize`, `withSeparator`, `collapsible`, `collapsedSize`, `children`.

**Rule:** the orchestrator (`report-engine.tsx`) imports only `Group` from `react-resizable-panels` directly. All resize/splitter UI is delegated to `Panel`. Content panel components in `components/{datasource,engine,properties}/` must not import `react-resizable-panels`.

### Three-column layout

```
┌──────────────┬──────────────────────────────────┬──────────────┐
│ 数据源配置    │          UniverSheet             │  属性设置     │
│ DataSource   │          SheetPanel              │  Property    │
│ (collapsible)│          (fixed, center)         │  (collapsible)│
└──────────────┴──────────────────────────────────┴──────────────┘
```

Left/right panels: 15% default, min 200px, collapsible. Center panel: 70%, not collapsible.

### Dependencies

- **Runtime:** `@univerjs/preset-sheets-core`, `@univerjs/presets`, `react-resizable-panels`, `rxjs`
- **Peer (consumer must provide):** `react >=18`, `react-dom >=18`, `antd >=5`, `@ant-design/icons >=5`
- UI components use antd (Button) and @ant-design/icons (MenuFoldOutlined, MenuUnfoldOutlined). Do not add antd to `dependencies` — it must remain a peerDependency.

### Build configuration

`rslib.config.ts`: `bundle: false`, `dts: true`, `format: 'esm'`. Path alias `@/` → `src/`. Output: `dist/` (ESM + .d.ts, published to npm).

### Demo app

`apps/app-pc/` is a minimal Rsbuild + React app that renders `<ReportEngine />` on its home page. Used to develop and verify the library. Has its own `AGENTS.md` with Rsbuild-specific commands (`dev`, `build`, `preview`).

## Project Conventions

### 组件化开发

不要在顶层文件中直接实现 UI，所有组件必须拆分到 `components/` 目录下。

- 每个功能域对应一个子目录（`datasource/`、`engine/`、`layout/`、`properties/`）
- 每个组件单独一个文件，职责单一
- 顶层文件（如 `report-engine.tsx`）只做编排和 re-export，不包含具体 UI 实现
- 新增功能时，先在对应域目录下创建组件文件，再从入口导出

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 文件名 | kebab-case（小写 + 短横线） | `report-engine.tsx`、`data-source-panel.tsx` |
| 组件名 | PascalCase | `ReportEngine`、`DataSourcePanel` |
| 目录名 | kebab-case | `components/engine/`、`components/layout/` |

### 禁止使用的工具

- **禁止使用 chrome-devtools MCP 工具**（如 `mcp__chrome-devtools__*` 系列工具）。不要通过浏览器 DevTools 进行页面操作、截图、快照、网络请求检查等。

### Coding principles

1. **Think before coding** — state assumptions explicitly; if multiple interpretations exist, present them rather than picking silently; if something is unclear, stop and ask.
2. **Simplicity first** — minimum code that solves the problem. No speculative features, no abstractions for single-use code, no error handling for impossible scenarios.
3. **Surgical changes** — touch only what you must. Don't "improve" adjacent code. Match existing style. Remove orphans from YOUR changes, but don't delete pre-existing dead code unless asked. Every changed line should trace directly to the user's request.
4. **Goal-driven execution** — define verifiable success criteria and loop until verified. For multi-step work, state a brief plan with `verify:` checkpoints.
