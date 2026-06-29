# Frontend Integration Guide

This guide explains how to embed the Report Engine designer/preview into your own React
application. The bundled `apps/app-pc` is a complete, runnable reference of everything here.

> **React 18+, TypeScript, and Ant Design 6 are required.** The component library is built for
> these and ships ESM only.

## 1. Package overview

The frontend is a pnpm monorepo with three publishable packages plus a demo app:

| Package | Role |
|---|---|
| `@coding-report/report-univer` | Univer spreadsheet React wrapper (snapshot import/export, fonts) |
| `@coding-report/report-api` | Backend API client (axios instance + auto-unwrapping of `SingleResponse`/`MultiResponse`) |
| `@coding-report/report-engine` | Report designer + data-source/data-model management components (pure UI, never calls APIs itself) |

Build/dependency order is `report-univer → report-api → report-engine`. The key design rule:
**`report-engine` components are pure UI** — they receive data via props and call back via
injected service objects; they never import `report-api` directly. You wire the two together in
your app (see §4).

## 2. Install

The packages are workspace packages in this repo (`workspace:*`). In your own project, depend on
the three published packages and their peers:

```jsonc
{
  "dependencies": {
    "@coding-report/report-univer": "0.0.1",
    "@coding-report/report-api": "0.0.1",
    "@coding-report/report-engine": "0.0.1",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "antd": "^6.2.1",
    "axios": "^1.7.9"
  }
}
```

`report-univer` depends on the `@univerjs/*` 0.25 packages (core, sheets, sheets-ui, presets,
engine-formula, etc.) — install the same set the demo app uses if your bundler does not hoist
them. See `apps/app-pc/package.json` for the exact list.

## 3. Point the API client at your backend

`report-api` ships a preconfigured axios instance with `baseURL: '/api'` and a response
interceptor that unwraps the `SingleResponse`/`MultiResponse` envelope automatically. You only
need to make `/api` reach the backend.

**Dev server proxy** (Rsbuild example, `rsbuild.config.ts`):

```ts
export default defineConfig({
  server: {
    proxy: {
      '/api': { target: 'http://127.0.0.1:8090', changeOrigin: true },
    },
  },
});
```

In production, serve the frontend behind the same origin as the backend, or front both with a
gateway that routes `/api` to the engine service.

## 4. The injection pattern

Every management component takes a `service` (or `*Service`) object whose methods you implement
by delegating to `report-api` functions. This keeps the library API-agnostic. Example — the
data-source management page is literally this:

```tsx
import { DataSourceManager } from '@coding-report/report-engine';
import type { DataSourceService } from '@coding-report/report-engine';
import {
  listDataSources, getDataSource, saveDataSource, deleteDataSource,
  introspectDatasets, introspectByConfig, introspectSql,
  uploadDataFile, testConnection, listDataSourceTypes,
} from '@coding-report/report-api';

const service: DataSourceService = {
  list: (current, pageSize) => listDataSources(current, pageSize),
  get: (id) => getDataSource(id),
  save: (dto) => saveDataSource(dto),
  remove: (id) => deleteDataSource(id),
  introspect: (id, tableNames) => introspectDatasets(id, tableNames),
  introspectByConfig: (dto, tableNames) => introspectByConfig(dto, tableNames),
  introspectSql: (id, sql) => introspectSql(id, sql),
  uploadDataFile: (file, type) => uploadDataFile(file, type),
  testConnection: (dto) => testConnection(dto),
  listDriverTypes: async () => (await listDataSourceTypes(1, 100)).list,
};

export default function DataSourcesPage() {
  return <DataSourceManager service={service} />;
}
```

The data-model management page follows the same pattern with `DataModelListPage` +
`DataModelService` / `DataModelDesignerService` (see `apps/app-pc/src/pages/datamodels.tsx`).

## 5. The report designer (`ReportEngine`)

`ReportEngine` is the three-pane designer (data model / spreadsheet / properties). Feed it the
data model pieces as props and inject a `renderService` to enable the built-in
preview/export/drill flow:

```tsx
import { ReportEngine } from '@coding-report/report-engine';
import type { ReportEngineHandle } from '@coding-report/report-engine';
import {
  loadReportConfig, saveReportConfig,
  previewReport, renderReport, drillReport,
  importExcel, fetchFonts, fetchFunctions,
} from '@coding-report/report-api';

const engineRef = useRef<ReportEngineHandle>(null);

// load a report by id, then populate datasets/relationships/transforms from
// config.dataModel (the backend enriches GET /configs/{id} with a model view)
// engineRef.current?.loadReportConfig(config)

return (
  <ReportEngine
    datasets={datasets}            // Dataset[] from the loaded data model
    relationships={relationships}  // Relationship[]
    transforms={transforms}        // TransformItem[] (data-transform dictionaries)
    dataModelId={dataModelId}
    functions={functions}          // ExpressionCatalog from fetchFunctions()
    engineRef={engineRef}
    renderService={{ preview: previewReport, export: renderReport, drill: drillReport }}
    onImport={(file) => importExcel(file)}
    onSaveReport={(config) => saveReportConfig({ ...config, dataModelId })}
    onFontRequest={fetchFonts}
  />
);
```

Header buttons (import / loop block / preview / export / save) are configurable via
`enableImport` / `enablePreview` / … and the `customActions` / `extraActions` slots. See
`apps/app-pc/src/pages/engine.tsx` for the full wiring including load-by-URL-id.

## 6. Standalone preview (`ReportPreview`)

For a read-only preview page (no designer), mount `ReportPreview` with a loaded config and a
`renderService`:

```tsx
import { ReportPreview } from '@coding-report/report-engine';

<ReportPreview
  config={loadedConfig}
  renderService={{ preview: previewReport, export: renderReport, drill: drillReport }}
  onClose={() => navigate('/reports')}
/>
```

The whole flow — parameter modal → render → preview drawer → drill-down → in-drawer export — is
encapsulated. The designer and the standalone page share this same component.

## 7. Routing & menu (the five pages)

The demo app exposes five routes; replicate as needed:

| Route | Component | Purpose |
|---|---|---|
| `/datasource-types` | driver management | register JDBC driver jars |
| `/datasources` | `DataSourceManager` | connections + datasets (physical & SQL) |
| `/datamodels` | `DataModelListPage` | data models (datasets / unions / relations / transforms) |
| `/reports` | report list (antd Table) | create / edit / preview / delete |
| `/engine`, `/preview` | `ReportEngine` / `ReportPreview` | entered from the report list |

Wrap your app in antd's `ConfigProvider` (+ `App`) as the demo does in
`apps/app-pc/src/index.tsx`.

## 8. Fonts

`UniverSheet` requests fonts through the `onFontRequest` callback you pass down (the demo uses
`fetchFonts` from `report-api`). The wrapper handles localStorage caching, `@font-face`
injection, and Univer registration internally — you only return the font list.

## 9. Key conventions to know

- **Display alias vs real id**: cells show aliases but transmit real ids. `value.payload` is the
  authoritative id (used for transport/export); `displayText` is a transient alias for display
  only and is **never sent to the backend**. Always derive display from `value`, never reverse a
  `displayText` back into a value.
- **Enum values are UPPERCASE string unions** (`'VERTICAL'`, `'SUM'`, `'FieldValue'`) aligned to
  the Java enum `name()`.
- **Data transforms**: bind a dictionary in a cell via the "数据转换" category in the expression
  builder, which inserts `map(field, "transformId")`. Transform items are configured under a data
  model (the designer's transforms tab).
- **`Value` has no Jackson polymorphism on the backend** — transport uses DTO records, so the
  frontend types mirror those DTOs exactly. When adding a field to `CellBinding`, update all
  layers (see the project `CLAUDE.md` for the five-place checklist).

## Reference & commands

`apps/app-pc` is the canonical working integration.

```bash
cd report-frontend
pnpm install
pnpm build            # builds report-univer → report-api → report-engine → app-pc
pnpm dev:app-pc       # starts the demo app (proxies /api to localhost:8090)
pnpm test             # library unit tests (rstest)
```
