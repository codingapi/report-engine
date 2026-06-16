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

export interface DataModelDataset {
  id: string;
  alias?: string;
  dataSourceType?: DataSourceType;
  fields: DataModelField[];
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

/** 报表所用的数据模型：数据集 + 数据关系（报表参数属报表级，前端管理，不在此） */
export interface DataModelInfo {
  datasets: DataModelDataset[];
  relationships: RelationshipInfo[];
}
