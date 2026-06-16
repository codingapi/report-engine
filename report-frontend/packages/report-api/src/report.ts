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
