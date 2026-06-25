import http from './http';
import type { DataModelInfo } from './datamodel';
import type { DataModelBrief } from './report';

// ============================================================
// Types
// ============================================================

/** 数据源连通性测试结果 */
export interface TestResult {
  ok: boolean;
  message: string;
  latencyMs: number;
}

/** 表字段元信息（探测数据源表结构时返回） */
export interface ColumnMeta {
  name: string;
  type: string;
  primaryKey: boolean;
}

/** 数据源测试请求体：既有数据源 id 或完整配置二选一 */
export interface DataSourceTestRequest {
  sourceId?: string;
  config?: Record<string, unknown>;
}

// ============================================================
// API
// ============================================================

/** 数据模型列表（id + name） */
export async function listDataModelBriefs(): Promise<DataModelBrief[]> {
  const res = await http.get('/datamodels');
  return res.data.list;
}

/** 获取数据模型详情（数据集 + 数据关系） */
export async function getDataModel(id: string): Promise<DataModelInfo> {
  const res = await http.get(`/datamodels/${id}`);
  return res.data as DataModelInfo;
}

/** 创建数据模型，返回新数据模型 id */
export async function createDataModel(data: DataModelInfo): Promise<string> {
  const res = await http.post('/datamodels', data);
  return res.data as string;
}

/** 更新数据模型，返回数据模型 id */
export async function updateDataModel(id: string, data: DataModelInfo): Promise<string> {
  const res = await http.put(`/datamodels/${id}`, data);
  return res.data as string;
}

/** 删除指定数据模型 */
export async function deleteDataModel(id: string): Promise<void> {
  await http.delete(`/datamodels/${id}`);
}

/** 测试数据源连通性（传入 sourceId 或完整 config） */
export async function testDataSource(req: DataSourceTestRequest): Promise<TestResult> {
  const res = await http.post('/datasources/test', req);
  return res.data as TestResult;
}

/** 探测数据源下所有表名 */
export async function exploreTables(sourceId: string): Promise<string[]> {
  const res = await http.get('/datasources/tables', {
    params: { sourceId },
  });
  return res.data.list;
}

/** 探测指定表的字段元信息 */
export async function exploreColumns(sourceId: string, table: string): Promise<ColumnMeta[]> {
  const res = await http.get('/datasources/columns', {
    params: { sourceId, table },
  });
  return res.data.list;
}
