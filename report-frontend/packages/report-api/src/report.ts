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
  /** 表达式预览（友好文本，后端仅存储不渲染） */
  preview?: string;
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

// ============================================================
// 报表配置持久化（保存 / 加载 / 列表）
// ============================================================

/** 报表列表项 */
export interface ReportBrief {
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

/** 示例报表列表（预存的测试报表） */
export async function listExampleReports(): Promise<ReportBrief[]> {
  const res = await http.get('/report/configs/examples');
  return res.data.list;
}

/** 报表列表（id + name） */
export async function listReportConfigs(): Promise<ReportBrief[]> {
  const res = await http.get('/report/configs');
  return res.data.list;
}
