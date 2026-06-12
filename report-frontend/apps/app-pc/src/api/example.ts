import http from './index';
import type { ExcelWorkbook } from '@coding-report/report-univer';

/**
 * 导出 Excel 文件
 *
 * POST /api/excel/generate
 * 将前端快照数据发送到后端，由 POI 构建 .xlsx 并返回 Blob
 */
export async function exportExcel(workbook: ExcelWorkbook): Promise<Blob> {
  const response = await http.post('/excel/generate', workbook, {
    responseType: 'blob',
  });
  return response.data as Blob;
}

/**
 * 导入 Excel 文件
 *
 * POST /api/excel/import
 * 上传 .xlsx 文件，后端解析为 Workbook JSON 返回
 */
export async function importExcel(file: File): Promise<ExcelWorkbook> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await http.post('/excel/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data as ExcelWorkbook;
}

/** 后端字体列表项（含文件名，用于 @font-face 加载） */
export interface FontItem {
  family: string;
  filename: string;
}

/**
 * 获取可用字体列表
 *
 * GET /api/fonts/list
 * 返回后端已注册的自定义字体（含文件名）
 */
export async function fetchFonts(): Promise<FontItem[]> {
  const response = await http.get('/fonts/list');
  return response.data as FontItem[];
}
