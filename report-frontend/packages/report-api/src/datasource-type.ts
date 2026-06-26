import http from './http';

// ============================================================
// Types (对齐后端 DataSourceTypeController / DataSourceTypeDTO)
// ============================================================

/** 数据源类型列表项（GET /api/datasource-types 返回） */
export interface DataSourceTypeBrief {
  id: string;
  name: string;
  /** 类型判别串，对齐 DataSourceType.type()（"DB" / "EXCEL" / "CSV"） */
  kind: string;
  createTime: number;
  updateTime: number;
}

/** 数据源类型持久化 DTO（POST /api/datasource-types 入参 / GET /{id} 出参） */
export interface DataSourceTypeDTO {
  id?: string;
  name: string;
  kind: string;
  jarFile: string;
  driverClass: string;
  createTime?: number;
  updateTime?: number;
}

/** 上传驱动 jar 响应：保存的 jar 文件名 + 扫描出的候选驱动类名 */
export interface DriverJarUploadResult {
  jarFile: string;
  driverClasses: string[];
}

/** 分页结果 */
export interface DataSourceTypePage {
  list: DataSourceTypeBrief[];
  total: number;
}

// ============================================================
// API
// ============================================================

/** 分页列表 */
export async function listDataSourceTypes(
  current = 1,
  pageSize = 10,
): Promise<DataSourceTypePage> {
  const res = await http.get('/datasource-types', { params: { current, pageSize } });
  return { list: res.data.list, total: res.data.total };
}

/** 详情 */
export async function getDataSourceType(id: string): Promise<DataSourceTypeDTO> {
  const res = await http.get(`/datasource-types/${id}`);
  return res.data as DataSourceTypeDTO;
}

/** 保存（含 id 更新，不含新建），返回 id */
export async function saveDataSourceType(dto: DataSourceTypeDTO): Promise<string> {
  const res = await http.post('/datasource-types', dto);
  return res.data as string;
}

/** 删除 */
export async function deleteDataSourceType(id: string): Promise<void> {
  await http.delete(`/datasource-types/${id}`);
}

/** 上传驱动 jar（multipart），返回 jar 文件名 + 候选驱动类名列表 */
export async function uploadDriverJar(file: File): Promise<DriverJarUploadResult> {
  const form = new FormData();
  form.append('file', file);
  const res = await http.post('/datasource-types/driver-jar', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data as DriverJarUploadResult;
}
