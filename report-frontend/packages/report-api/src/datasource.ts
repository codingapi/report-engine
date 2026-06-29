import http from './http';
import type {
  DataModelInfo,
  DataModelDataset,
  DataModelSource,
  RelationshipInfo,
  TransformItemInfo,
} from './datamodel';
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

/** 数据源类型判别串（对齐后端 DataSourceType.type()） */
export type DataSourceKind = 'DB' | 'EXCEL' | 'CSV';

/** 数据源列表项（GET /api/datasources 返回，不含 config 敏感字段） */
export interface DataSourceBrief {
  id: string;
  name: string;
  type: DataSourceKind;
  typeConfigId?: string;
  /** 该连接下已解析的数据集（表/sheet）数量 */
  datasetCount: number;
  createTime: number;
  updateTime: number;
}

/** 数据集字段契约（对齐后端 FieldDTO） */
export interface DatasetFieldDTO {
  name: string;
  alias: string;
  dataType: string;
  primaryKey: boolean;
}

/** 数据源下的数据集契约（对齐后端 DatasetDTO 的 TABLE 形态） */
export interface DatasetDTO {
  id?: string;
  /** 数据集标识名（物理表=表名，SQL 数据集=用户自定义） */
  name?: string;
  alias: string;
  kind: 'TABLE';
  datasourceId?: string;
  /** 取数来源：物理表名 或 一段 SELECT SQL */
  sourceTable: string;
  fields: DatasetFieldDTO[];
  members?: null;
}

/** 数据源持久化 DTO（POST /api/datasources 入参 / GET /{id} 出参） */
export interface DataSourceDTO {
  id?: string;
  name: string;
  type: DataSourceKind;
  typeConfigId?: string;
  /** 配置（含敏感字段，出口由后端脱敏；保存时 `***` 占位回填旧值） */
  config: Record<string, unknown>;
  /** 该连接下维护的数据集（表别名/字段别名）；编辑回填用 */
  datasets?: DatasetDTO[];
}

/** 元数据解析：一张表/sheet/CSV 文件解析出的列元数据 */
export interface IntrospectedColumn {
  name: string;
  dataType: string;
  primaryKey: boolean;
  /** 字段备注（DB 类型来自 JDBC REMARKS），用作字段别名默认值；CSV/Excel 无 */
  remark?: string;
}

export interface IntrospectedTable {
  name: string;
  columns: IntrospectedColumn[];
  /** 数据源下已保存的数据集别名（后端合并回填），供添加数据集时展示「别名（表名）」 */
  alias?: string;
}

/** 文件上传响应：保存路径 + 解析出的表列表（前端据此构造 DataSourceDTO.config.path） */
export interface DataFileUploadResult {
  savedPath: string;
  type: DataSourceKind;
  tables: IntrospectedTable[];
}

/** 分页结果 */
export interface DataSourcePage {
  list: DataSourceBrief[];
  total: number;
}

/** 数据模型分页结果 */
export interface DataModelPage {
  list: DataModelBrief[];
  total: number;
}

/**
 * 数据模型保存 DTO（对齐后端 DataModelDTO record）。
 * - 新建：不传 id，至少传 name；datasets/relationships/datasources 可空。
 * - 更新：传 id；敏感字段回填 `***` 由后端按旧值合并。
 */
export interface DataModelSaveDTO {
  id?: string;
  name: string;
  status?: string;
  createTime?: number;
  updateTime?: number;
  datasources?: DataModelSource[];
  datasets?: DataModelDataset[];
  relationships?: RelationshipInfo[];
  transforms?: TransformItemInfo[];
}

// ============================================================
// API
// ============================================================

/** 数据模型列表（id + name，不带分页参数，后端默认返回首页） */
export async function listDataModelBriefs(): Promise<DataModelBrief[]> {
  const res = await http.get('/datamodels');
  return res.data.list;
}

/** 数据模型分页列表（按 current/pageSize 查询） */
export async function listDataModelsPage(
  current = 1,
  pageSize = 10,
): Promise<DataModelPage> {
  const res = await http.get('/datamodels', { params: { current, pageSize } });
  return { list: res.data.list, total: res.data.total };
}

/** 获取数据模型详情（数据集 + 数据关系） */
export async function getDataModel(id: string): Promise<DataModelInfo> {
  const res = await http.get(`/datamodels/${id}`);
  return res.data as DataModelInfo;
}

/** 创建数据模型，返回新数据模型 id */
export async function createDataModel(data: DataModelSaveDTO): Promise<string> {
  const res = await http.post('/datamodels', data);
  return res.data as string;
}

/** 更新数据模型，返回数据模型 id（后端 POST /datamodels 为 upsert，靠 id 区分新建/更新） */
export async function updateDataModel(
  id: string,
  data: DataModelSaveDTO,
): Promise<string> {
  const res = await http.post('/datamodels', { ...data, id });
  return res.data as string;
}

/** 发布数据模型（草稿 → 已发布） */
export async function publishDataModel(id: string): Promise<void> {
  await http.post(`/datamodels/${id}/publish`);
}

/** 撤销发布数据模型（已发布 → 草稿） */
export async function unpublishDataModel(id: string): Promise<void> {
  await http.post(`/datamodels/${id}/unpublish`);
}

/** 删除指定数据模型 */
export async function deleteDataModel(id: string): Promise<void> {
  await http.post(`/datamodels/${id}/delete`);
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

// ============================================================
// 数据源 CRUD + 元数据解析 + 文件上传 + 连接测试
// ============================================================

/** 分页列表 */
export async function listDataSources(current = 1, pageSize = 10): Promise<DataSourcePage> {
  const res = await http.get('/datasources', { params: { current, pageSize } });
  return { list: res.data.list, total: res.data.total };
}

/** 详情（凭证字段脱敏） */
export async function getDataSource(id: string): Promise<DataSourceDTO> {
  const res = await http.get(`/datasources/${id}`);
  return res.data as DataSourceDTO;
}

/** 保存（含 id 更新，不含新建），返回 id */
export async function saveDataSource(dto: DataSourceDTO): Promise<string> {
  const res = await http.post('/datasources', dto);
  return res.data as string;
}

/** 删除 */
export async function deleteDataSource(id: string): Promise<void> {
  await http.post(`/datasources/${id}/delete`);
}

/** 元数据解析：返回表/sheet + 列定义。tableNames 指定只解析哪些表（空=全部） */
export async function introspectDatasets(
  id: string,
  tableNames?: string[],
): Promise<IntrospectedTable[]> {
  const res = await http.post(
    `/datasources/${id}/introspect`,
    tableNames && tableNames.length > 0 ? { tableNames } : {},
  );
  return res.data.list;
}

/** 自定义 SQL 数据集探查：按连接 id 执行 SQL 推断列定义（不落库） */
export async function introspectSql(id: string, sql: string): Promise<ColumnMeta[]> {
  const res = await http.post(`/datasources/${id}/introspect-sql`, { sql });
  return res.data.list;
}

/** 元数据解析：按配置直接解析（不落库），供数据源向导「解析」用。tableNames 指定只解析哪些表（空=全部） */
export async function introspectByConfig(
  dto: DataSourceDTO,
  tableNames?: string[],
): Promise<IntrospectedTable[]> {
  const body = tableNames && tableNames.length > 0 ? { ...dto, tableNames } : dto;
  const res = await http.post('/datasources/introspect', body);
  return res.data.list;
}

/** 上传 Excel/CSV 文件并解析元数据（multipart） */
export async function uploadDataFile(file: File, type?: DataSourceKind): Promise<DataFileUploadResult> {
  const form = new FormData();
  form.append('file', file);
  const res = await http.post('/datasources/upload', form, {
    params: type ? { type } : undefined,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data as DataFileUploadResult;
}

/** 连接测试（按完整 DTO；DB 需 typeConfigId/url/账密；EXCEL/CSV 需 config.path） */
export async function testConnection(dto: DataSourceDTO): Promise<TestResult> {
  const res = await http.post('/datasources/test', dto);
  return res.data as TestResult;
}
