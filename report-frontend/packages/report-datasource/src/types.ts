import type { DataType } from '@coding-report/report-api';

/** 数据源类型（对齐后端 DataSourceType 枚举） */
export type DataSourceType = 'CSV' | 'JSON' | 'DB' | 'API' | 'EXCEL';

/** JOIN 类型（对齐后端 JoinType 枚举） */
export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';

// ============================================================
// 数据源 / 数据集 / 关系 类型
// ============================================================

/** 数据源连接配置（对齐后端 DataSource 实体，#30 完成 API 客户端时再校准字段名） */
export interface DataSourceConfig {
  id: string;
  name: string;
  type: DataSourceType;
  /** 连接 URL（DB JDBC/CSV 路径/JSON URL/API endpoint） */
  url?: string;
  /** DB 用户名 / API 认证用户名 */
  username?: string;
  /** DB 密码 / API Token（前端只读展示，不回显明文） */
  password?: string;
  /** 额外选项（JSON 字符串或键值对，交给后端解析） */
  options?: Record<string, unknown>;
  /** 创建时间戳 */
  createTime?: number;
  /** 更新时间戳 */
  updateTime?: number;
}

/** 表信息（探查树叶子） */
export interface TableInfo {
  /** 物理表名 */
  name: string;
  /** 表别名/注释（可空） */
  comment?: string;
  /** 行数估算（可空） */
  rowCount?: number;
}

/** 列信息（探查树展开后的字段） */
export interface ColumnInfo {
  name: string;
  dataType: DataType;
  comment?: string;
  nullable?: boolean;
  primaryKey?: boolean;
}

/** 物理数据集：从某数据源选某张表 */
export interface PhysicalDataset {
  id: string;
  alias?: string;
  sourceId: string;
  table: string;
  fields: DatasetFieldDef[];
}

/** UNION 派生数据集：基于多个物理数据集纵向合并 */
export interface UnionDatasetDef {
  id: string;
  alias?: string;
  /** 参与并集的物理数据集 id 列表（≥2） */
  baseDatasetIds: string[];
  fields: DatasetFieldDef[];
}

/** 数据集统一描述（sealed-like：kind 区分物理 / UNION） */
export type DatasetDef =
  | ({ kind: 'PHYSICAL' } & PhysicalDataset)
  | ({ kind: 'UNION' } & UnionDatasetDef);

/** 数据集字段定义（数据源管理视角，与报表引擎复用） */
export interface DatasetFieldDef {
  name: string;
  alias?: string;
  dataType: DataType;
  primaryKey?: boolean;
}

/** 字段引用 */
export interface FieldRef {
  datasetId: string;
  field: string;
}

/** 数据集间关系（JOIN） */
export interface Relationship {
  id: string;
  left: FieldRef;
  right: FieldRef;
  joinType: JoinType;
}

// ============================================================
// 数据源管理服务（依赖注入接口）
// ------------------------------------------------------------
// 后端 REST API 已存在但 report-api 的客户端函数尚未实现（Issue #30）。
// 本包 hooks 通过此接口注入回调，不在包内直接发请求；
// 使用方（app-pc）暂时可注入占位实现，#30 完成后由 report-api 提供真实实现。
// ============================================================

export interface DatasourceService {
  listDataSources?(): Promise<DataSourceConfig[]>;
  createDataSource?(config: Omit<DataSourceConfig, 'id'>): Promise<string>;
  updateDataSource?(config: DataSourceConfig): Promise<void>;
  deleteDataSource?(id: string): Promise<void>;
  /** 测试连接：返回 ok=true/false + 失败信息 */
  testConnection(config: {
    type?: DataSourceType;
    url?: string;
    username?: string;
    password?: string;
    options?: Record<string, unknown>;
  }): Promise<{ ok: boolean; message?: string }>;
  /** 表列表 */
  exploreTables(sourceId: string): Promise<TableInfo[]>;
  /** 列信息 */
  exploreColumns(sourceId: string, table: string): Promise<ColumnInfo[]>;
}

// ============================================================
// 组件 Props
// ============================================================

export interface ConnectionFormProps {
  /** 受控值 */
  value?: Partial<DataSourceConfig>;
  /** 值变更回调（antd Form.Item 语义，回调为 form values 整体） */
  onChange?: (value: Partial<DataSourceConfig>) => void;
  /** 注入服务；不传则禁用「测试连接」按钮 */
  service?: DatasourceService;
  /** 受控测试结果（可选；不传则内部自管） */
  testResult?: { ok: boolean; message?: string };
  /** 测试结果变更回调（受控模式配套） */
  onTestResultChange?: (result: { ok: boolean; message?: string } | null) => void;
  /** 是否禁用 */
  disabled?: boolean;
}

export interface ExploreTreeProps {
  /** 当前选中数据源 id */
  sourceId?: string;
  /** 注入服务（必须提供 exploreTables/exploreColumns） */
  service?: DatasourceService;
  /** 选中表时回调 */
  onSelectTable?: (table: TableInfo | null) => void;
  /** 选中列时回调 */
  onSelectColumn?: (column: ColumnInfo | null) => void;
  /** 默认展开的表名列表 */
  defaultExpandedTables?: string[];
}

export interface DatasetManagerProps {
  /** 数据源下已有数据集列表 */
  datasets: DatasetDef[];
  /** 可选数据源列表（创建物理数据集时选源） */
  dataSources: DataSourceConfig[];
  /** 注入服务（探查表/列用，可选） */
  service?: DatasourceService;
  /** 数据集变更回调（增删改） */
  onChange?: (datasets: DatasetDef[]) => void;
}

export interface RelationEditorProps {
  /** 数据集列表（关系引用其中的 id/alias） */
  datasets: DatasetDef[];
  /** 已有关系列表 */
  relationships: Relationship[];
  /** 关系变更回调 */
  onChange?: (relationships: Relationship[]) => void;
  /** 是否禁用 */
  disabled?: boolean;
}
