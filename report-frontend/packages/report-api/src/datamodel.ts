import type { DataType } from './dataset';

// ============================================================
// Types（匹配后端 ReportConfigController 返回的 dataModel 结构）
// ============================================================

/** 数据源类型（对齐后端 DataSourceType 枚举） */
export type DataSourceType = 'CSV' | 'JSON' | 'DB' | 'API' | 'EXCEL';

/** JOIN 类型 */
export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';

export interface DataModelField {
  name: string;
  alias?: string;
  dataType: DataType;
  primaryKey?: boolean;
}

/** UNION 成员（对齐后端 UnionMemberDTO：统一列名 → 成员实际字段名） */
export interface UnionMember {
  datasetId: string;
  mapping?: Record<string, string>;
}

/**
 * 数据集（兼容两种端点返回，字段按需可选）：
 * - {@code GET /api/report/configs/{id}} 附带的 {@code dataModel.datasets}：精简视图
 *   {@code { id, alias, dataSourceType, fields }}（无 kind/datasourceId/sourceTable/members）
 * - {@code GET /api/datamodels/{id}} 返回的 {@code datasets}：完整 DatasetDTO
 *   {@code { id, alias, kind, datasourceId, sourceTable, fields, members }}（无 dataSourceType）
 */
export interface DataModelDataset {
  id: string;
  /** 英文标识名（TABLE=物理表名 sourceTable、UNION=合集名） */
  name?: string;
  alias?: string;
  /** 数据源类型（仅 configs/{id} 精简视图返回） */
  dataSourceType?: DataSourceType;
  /** 数据集形态（仅 datamodels/{id} 完整 DTO 返回）：TABLE | UNION */
  kind?: 'TABLE' | 'UNION';
  /** 物理表所属数据源 id（kind=TABLE） */
  datasourceId?: string;
  /** 物理表名/查询（kind=TABLE） */
  sourceTable?: string;
  /** UNION 成员（kind=UNION） */
  members?: UnionMember[];
  fields: DataModelField[];
}

/** 转换项条目（对齐后端 TransformItemDTO.EntryDTO：code/label/parent） */
export interface TransformEntryInfo {
  /** 原始编码（字段实际存储值） */
  code: string;
  /** 呈现文本（映射后展示） */
  label: string;
  /** 父级编码（树形用，顶层为空） */
  parent?: string;
}

/** 数据转换项（对齐后端 TransformItemDTO）：编码 → 呈现映射，报表中由 map(字段, 转换项id) 引用 */
export interface TransformItemInfo {
  id: string;
  /** 标识名（引用名） */
  name: string;
  /** 别名（中文名，展示用） */
  alias?: string;
  entries: TransformEntryInfo[];
}

export interface FieldRefInfo {
  datasetId: string;
  field: string;
}

export interface RelationshipInfo {
  left: FieldRefInfo;
  right: FieldRefInfo;
  joinType: JoinType;
}

/** 数据源连接（对齐后端 DataSourceDTO：{@code { id, name, type, config }}，敏感字段后端已脱敏） */
export interface DataModelSource {
  id: string;
  name: string;
  type: DataSourceType;
  /** 连接配置（含 path/url/凭证等，后端出口已脱敏） */
  config?: Record<string, unknown>;
}

/** 报表所用的数据模型：数据集 + 数据关系（报表参数属报表级，前端管理，不在此） */
export interface DataModelInfo {
  datasets: DataModelDataset[];
  relationships: RelationshipInfo[];
  /** 数据源连接列表（仅 GET /api/datamodels/{id} 完整返回；精简视图不含） */
  datasources?: DataModelSource[];
  /** 用户自定义转换项（在数据模型下配置，报表用 map() 引用） */
  transforms?: TransformItemInfo[];
}
