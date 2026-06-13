import http from './http';
import type { ExcelWorkbook } from '@coding-report/report-univer';

export async function exportExcel(workbook: ExcelWorkbook): Promise<Blob> {
  const response = await http.post('/excel/generate', workbook, {
    responseType: 'blob',
  });
  return response.data as Blob;
}

export async function importExcel(file: File): Promise<ExcelWorkbook> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await http.post('/excel/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data as ExcelWorkbook;
}
