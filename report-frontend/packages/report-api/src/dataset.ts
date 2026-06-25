import http from './http';

// ============================================================
// Types
// ============================================================

export type DataType = 'STRING' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'JSON';

export interface DatasetField {
  name: string;
  alias?: string;
  dataType: DataType;
  primaryKey?: boolean;
}

export interface DatasetInfo {
  id: string;
  alias?: string;
  dataSourceId: string;
  dataSourceType: string;
  fields: DatasetField[];
}

export interface DatasetPreview {
  columns: string[];
  rows: Record<string, unknown>[];
}

// ============================================================
// API
// ============================================================

/** 获取指定数据模型下的数据集列表（含字段定义） */
export async function fetchDatasets(dataModelId: string): Promise<DatasetInfo[]> {
  const res = await http.get('/datasets', { params: { dataModelId } });
  return res.data.list;
}

/** 获取数据集前 N 行预览数据 */
export async function fetchDatasetPreview(id: string, limit = 20): Promise<DatasetPreview> {
  const res = await http.get(`/datasets/${id}/preview`, { params: { limit } });
  return res.data;
}
