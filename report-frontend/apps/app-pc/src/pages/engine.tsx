import { useEffect, useState } from 'react';
import { Spin } from 'antd';
import { ReportEngine } from '@coding-report/report-engine';
import type { Dataset, CellBinding, LoopBlock, SummaryRow } from '@coding-report/report-engine';
import { exportExcel, importExcel, fetchFonts, fetchDatasets } from '@coding-report/report-api';
import type { ExcelWorkbook } from '@coding-report/report-univer';

const EnginePage = () => {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchDatasets()
      .then((list) => {
        // DatasetInfo → Dataset: alias fallback to id
        setDatasets(
          list.map((d) => ({
            id: d.id,
            alias: d.alias || d.id,
            fields: d.fields.map((f) => ({
              name: f.name,
              alias: f.alias || f.name,
              dataType: f.dataType,
              primaryKey: f.primaryKey,
            })),
          })),
        );
      })
      .catch((e) => console.error('加载数据集失败:', e))
      .finally(() => setLoading(false));
  }, []);

  const handleImport = async (file: File): Promise<ExcelWorkbook> => {
    return importExcel(file);
  };

  const handleExport = async (
    bindings: CellBinding[],
    loops: LoopBlock[],
    summaries: SummaryRow[],
    workbook: ExcelWorkbook,
  ): Promise<void> => {
    // 当前阶段：直接导出表格快照（后续阶段：拼装 Report JSON → render API）
    console.log('导出配置:', { bindings, loops, summaries });
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

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
        <Spin size="large" tip="加载数据集..." />
      </div>
    );
  }

  return (
    <ReportEngine
      datasets={datasets}
      title="报表设计器"
      onImport={handleImport}
      onExport={handleExport}
      onFontRequest={fetchFonts}
    />
  );
};

export default EnginePage;
