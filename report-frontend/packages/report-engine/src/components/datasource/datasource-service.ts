import type {
  DataSourceBrief,
  DataSourceDTO,
  DataSourceKind,
  DataFileUploadResult,
  DataSourceTypeBrief,
  DatasetDTO,
  IntrospectedTable,
  TestResult,
} from '@coding-report/report-api';

/**
 * 数据源管理对外依赖的服务注入（app-pc 用 report-api 实现）。
 * 组件本身不直接调 API，保持纯 UI 可复用。
 */
export interface DataSourceService {
  list: (current: number, pageSize: number) => Promise<{
    list: DataSourceBrief[];
    total: number;
  }>;
  get: (id: string) => Promise<DataSourceDTO>;
  save: (dto: DataSourceDTO) => Promise<string>;
  remove: (id: string) => Promise<void>;
  introspect: (id: string) => Promise<IntrospectedTable[]>;
  uploadDataFile: (file: File, type?: DataSourceKind) => Promise<DataFileUploadResult>;
  testConnection: (dto: DataSourceDTO) => Promise<TestResult>;
  /** DB 驱动下拉用：列出已注册的 DB 驱动 */
  listDriverTypes?: () => Promise<DataSourceTypeBrief[]>;
}

/** 编辑器内可维护的字段（含别名）。 */
export interface WizardField {
  name: string;
  alias: string;
  dataType: string;
  primaryKey: boolean;
}

/** 编辑器内可维护的表/数据集（含别名、可删除）。 */
export interface WizardTable {
  /** 已存在数据集的 id（编辑回填时带），新解析的为空 */
  id?: string;
  name: string;
  alias: string;
  columns: WizardField[];
}

/** IntrospectedTable → 维护表（表别名取数据源已配置别名、字段别名默认取 DB 备注 remark）。 */
export function fromIntrospected(t: IntrospectedTable): WizardTable {
  return {
    name: t.name,
    alias: t.alias ?? t.name,
    columns: t.columns.map((c) => ({
      name: c.name,
      alias: c.remark || c.name,
      dataType: c.dataType,
      primaryKey: c.primaryKey,
    })),
  };
}

/** 数据集 DTO → 维护表（编辑回填）。 */
export function fromDatasetDto(d: DatasetDTO): WizardTable {
  return {
    id: d.id,
    name: d.sourceTable,
    alias: d.alias ?? d.sourceTable,
    columns: (d.fields ?? []).map((f) => ({
      name: f.name,
      alias: f.alias ?? f.name,
      dataType: f.dataType,
      primaryKey: f.primaryKey,
    })),
  };
}

/** 维护表 → 数据集 DTO（保存用）。 */
export function tablesToDatasetDtos(
  tables: WizardTable[],
  datasourceId?: string,
): DatasetDTO[] {
  return tables.map((t) => ({
    id: t.id,
    alias: t.alias,
    kind: 'TABLE' as const,
    datasourceId,
    sourceTable: t.name,
    fields: t.columns.map((c) => ({
      name: c.name,
      alias: c.alias,
      dataType: c.dataType,
      primaryKey: c.primaryKey,
    })),
    members: null,
  }));
}

/** 重新解析时按表名/字段名保留已编辑的别名。 */
export function mergeTables(prev: WizardTable[], fresh: WizardTable[]): WizardTable[] {
  return fresh.map((f) => {
    const p = prev.find((t) => t.name === f.name);
    if (!p) return f;
    return {
      ...f,
      id: p.id,
      alias: p.alias,
      columns: f.columns.map((fc) => {
        const pc = p.columns.find((c) => c.name === fc.name);
        return pc ? { ...fc, alias: pc.alias } : fc;
      }),
    };
  });
}
