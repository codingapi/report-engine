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
