import http from './http';
import type { ExcelWorkbook } from '@coding-report/report-univer';

// ============================================================
// Types (matches backend RenderRequest DTOs)
// ============================================================

export interface RenderValueDTO {
  type: string;
  payload?: string;
  aggregation?: string;
  operand?: RenderValueDTO;
  funcName?: string;
  args?: RenderValueDTO[];
  parts?: Array<{ kind: 'text' | 'hole'; text?: string; value?: RenderValueDTO }>;
}

export interface RenderConditionDTO {
  id: string;
  left: RenderValueDTO;
  operator: string;
  right: RenderValueDTO | null;
}

export interface RenderBindingDTO {
  cellKey: string;
  value: RenderValueDTO;
  expansion: string;
  expandMode: string;
  mergeRepeated: boolean;
  parentCell: string | null;
  conditions: RenderConditionDTO[];
  /** 独立纵向带：true 时本列从自己的声明行独立展开，不与同源列对齐 */
  independent?: boolean;
  /** 表达式预览（友好文本，后端仅存储不渲染） */
  preview?: string;
  /** 是否开启反查（drill-down）能力（默认 false） */
  drillEnabled?: boolean;
  /** 反查视图（数据集 id，可 null；null 时回退到该格字段所属数据集） */
  drillView?: string | null;
}

export interface RenderRequest {
  cellBindings: RenderBindingDTO[];
  loopBlocks: unknown[];
  summaries: unknown[];
  /** 报表参数值（name → value），后端构 ParamContext */
  params?: Record<string, unknown>;
  template: ExcelWorkbook;
}

// ============================================================
// API
// ============================================================

/** 渲染报表：发送配置 + 模板 → 返回填充数据的 .xlsx Blob */
export async function renderReport(request: RenderRequest): Promise<Blob> {
  const res = await http.post('/report/render', request, {
    responseType: 'blob',
  });
  return res.data;
}

/** 预览响应：工作簿 + 反查格坐标列表（"row:col" 格式） */
export interface PreviewResult {
  workbook: ExcelWorkbook;
  drillable: string[];
}

/** 预览报表：发送配置 + 模板 → 返回填充数据的工作簿 JSON + 反查格坐标列表（供前端 HTML 渲染预览） */
export async function previewReport(request: RenderRequest): Promise<PreviewResult> {
  const res = await http.post('/report/preview', request);
  return res.data as PreviewResult;
}

/** 反查请求：渲染配置 + 目标格坐标 */
export interface DrillRequestParams {
  request: RenderRequest;
  row: number;
  col: number;
}

/** 字段信息：name + alias */
export interface DrillFieldInfo {
  name: string;
  alias: string | null;
}

/** 反查结果：数据集 id/别名 + 字段列表 + 明细行（本期全量返回，不分页） */
export interface DrillResult {
  datasetId: string | null;
  alias: string | null;
  fields: DrillFieldInfo[];
  rows: Array<Record<string, unknown>>;
}

/** 反查明细：传入渲染配置 + 目标格坐标，返回该格贡献的原始数据行 */
export async function drillReport(params: DrillRequestParams): Promise<DrillResult> {
  const res = await http.post('/report/drill', params);
  return res.data as DrillResult;
}

// ============================================================
// 报表配置持久化（保存 / 加载 / 列表）
// ============================================================

/** 报表列表项 */
export interface ReportBrief {
  id: string;
  name: string;
  dataModelId?: string | null;
  createTime?: number;
  updateTime?: number;
}

/** 数据模型简要信息 */
export interface DataModelBrief {
  id: string;
  name: string;
}

/** 保存报表配置（含 id 则更新），返回报表 id */
export async function saveReportConfig(config: unknown): Promise<string> {
  const res = await http.post('/report/configs', config);
  return res.data as string;
}

/** 加载指定报表的完整配置（附带数据模型信息），T 为业务方期望的配置形态 */
export async function loadReportConfig<T = Record<string, unknown>>(id: string): Promise<T> {
  const res = await http.get(`/report/configs/${id}`);
  return res.data as T;
}

/** 删除指定报表配置 */
export async function deleteReportConfig(id: string): Promise<void> {
  await http.delete(`/report/configs/${id}`);
}

/** 报表列表分页结果（对齐后端 MultiResponse） */
export interface ReportPage {
  list: ReportBrief[];
  total: number;
}

/** 报表列表（id + name + dataModelId + 时间戳，含示例与用户报表），按 SearchRequest 分页查询 */
export async function listReportConfigs(current = 1, pageSize = 10): Promise<ReportPage> {
  const res = await http.get('/report/configs', { params: { current, pageSize } });
  return { list: res.data.list, total: res.data.total };
}

/** 数据模型列表（id + name，供创建报表时选择） */
export async function listDataModels(): Promise<DataModelBrief[]> {
  const res = await http.get('/datamodels');
  return res.data.list;
}
