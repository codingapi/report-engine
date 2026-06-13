import React from 'react';
import { ReportEngine } from '@coding-report/report-engine';
import { exportExcel, importExcel, fetchFonts } from '@coding-report/report-api';
import type { ExcelWorkbook } from '@coding-report/report-univer';
import { mockDataConfig } from '@/data/mock-data';

const EnginePage = () => {
  const handleImport = async (file: File): Promise<ExcelWorkbook> => {
    return importExcel(file);
  };

  const handleExport = async (workbook: ExcelWorkbook): Promise<void> => {
    const blob = await exportExcel(workbook);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `report-${new Date().toISOString().slice(0, 10)}.xlsx`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <ReportEngine
      dataConfig={mockDataConfig}
      title="销售数据月报"
      onImport={handleImport}
      onExport={handleExport}
      onFontRequest={fetchFonts}
    />
  );
};

export default EnginePage;
