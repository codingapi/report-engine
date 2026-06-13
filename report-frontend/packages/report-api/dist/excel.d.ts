import type { ExcelWorkbook } from '@coding-report/report-univer';
export declare function exportExcel(workbook: ExcelWorkbook): Promise<Blob>;
export declare function importExcel(file: File): Promise<ExcelWorkbook>;
