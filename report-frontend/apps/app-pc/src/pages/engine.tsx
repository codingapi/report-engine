import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spin, Button, message } from 'antd';
import { PrinterOutlined } from '@ant-design/icons';
import { ReportEngine } from '@coding-report/report-engine';
import type { ReportEngineHandle, ReportConfig } from '@coding-report/report-engine';
import type { Dataset, CellBinding, LoopBlock, SummaryRow, ReportParam, Relationship, ReportValue } from '@coding-report/report-engine';
import {
  importExcel, fetchFonts, renderReport, fetchFunctions,
  saveReportConfig, loadReportConfig,
} from '@coding-report/report-api';
import type { RenderBindingDTO, RenderValueDTO, ExpressionCatalog, DataModelInfo } from '@coding-report/report-api';
import type { ExcelWorkbook } from '@coding-report/report-univer';

// ─── 转换函数 ──────────────────────────────────

function toValueDTO(value: ReportValue): RenderValueDTO {
  return {
    type: value.type,
    payload: value.payload,
    aggregation: value.aggregation,
    operand: value.operand ? toValueDTO(value.operand) : undefined,
    funcName: value.funcName,
    args: value.args?.map(toValueDTO),
    parts: value.parts?.map((p) => ({
      kind: p.kind,
      text: p.text,
      value: p.value ? toValueDTO(p.value) : undefined,
    })),
  };
}

function toBindingDTO(binding: CellBinding): RenderBindingDTO {
  return {
    cellKey: binding.cellKey,
    value: toValueDTO(binding.value),
    expansion: binding.expansion,
    expandMode: binding.expandMode,
    mergeRepeated: binding.mergeRepeated,
    parentCell: binding.parentCell,
    conditions: binding.conditions.map((c) => ({
      id: c.id,
      left: toValueDTO(c.left),
      operator: c.operator,
      right: c.right ? toValueDTO(c.right) : null,
    })),
    independent: binding.independent ?? false,
    preview: binding.preview,
  };
}

/** 加载的报表配置（附带后端注入的数据模型信息） */
interface LoadedReportConfig extends ReportConfig {
  dataModel?: DataModelInfo;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// ─── 页面组件 ──────────────────────────────────

const EnginePage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const reportIdFromUrl = searchParams.get('id');

  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [relationships, setRelationships] = useState<Relationship[]>([]);
  const [dataModelId, setDataModelId] = useState<string>('default');
  const [functions, setFunctions] = useState<ExpressionCatalog>();
  const [loading, setLoading] = useState(true);
  const engineRef = useRef<ReportEngineHandle>(null);

  const handlePrintConfig = () => {
    const config = engineRef.current?.getReportConfig();
    if (!config) {
      message.warning('表格为空，无配置可打印');
      return;
    }
    console.log('[ReportConfig object]', config);
    console.log('[ReportConfig JSON]\n', JSON.stringify(config, null, 2));
  };

  // 加载公式目录
  useEffect(() => {
    fetchFunctions()
      .then(setFunctions)
      .catch((e) => console.error('加载公式列表失败:', e));
  }, []);

  // 根据 URL 参数加载或创建报表
  useEffect(() => {
    const init = async () => {
      if (reportIdFromUrl) {
        try {
          const config = await loadReportConfig<LoadedReportConfig>(reportIdFromUrl);
          const dm = config.dataModel;
          if (dm) {
            setDatasets(
              dm.datasets.map((d) => ({
                id: d.id,
                alias: d.alias || d.id,
                sourceType: d.dataSourceType || 'CSV',
                fields: d.fields.map((f) => ({
                  name: f.name,
                  alias: f.alias || f.name,
                  dataType: f.dataType,
                  primaryKey: f.primaryKey,
                })),
              })),
            );
            setRelationships(
              dm.relationships.map((r) => ({
                left: r.left,
                right: r.right,
                joinType: r.joinType,
              })),
            );
          }
          if (config.dataModelId) setDataModelId(config.dataModelId);
          engineRef.current?.loadReportConfig(config);
        } catch (e) {
          console.error('加载报表失败:', e);
        } finally {
          setLoading(false);
        }
      } else {
        // 无 id → 创建空白报表并跳转
        try {
          const newId = await saveReportConfig({
            name: '未命名报表',
            dataModelId: 'default',
          });
          navigate(`/engine?id=${newId}`, { replace: true });
        } catch (e) {
          console.error('创建报表失败:', e);
          setLoading(false);
        }
      }
    };
    init();
  }, [reportIdFromUrl, navigate]);

  const handleImport = async (file: File): Promise<ExcelWorkbook> => {
    return importExcel(file);
  };

  const handleExport = async (
    bindings: CellBinding[],
    loops: LoopBlock[],
    summaries: SummaryRow[],
    workbook: ExcelWorkbook,
    params: ReportParam[],
  ): Promise<void> => {
    const paramValues = Object.fromEntries(
      params.map((p) => [p.name, p.defaultValue ?? null]),
    );
    const blob = await renderReport({
      cellBindings: bindings.map(toBindingDTO),
      loopBlocks: loops,
      summaries: summaries,
      params: paramValues,
      template: workbook,
    });
    downloadBlob(blob, `report-${new Date().toISOString().slice(0, 10)}.xlsx`);
  };

  const handleSaveReport = async (config: ReportConfig): Promise<string> => {
    return saveReportConfig({ ...config, dataModelId });
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
        <Spin size="large" tip="加载报表..." />
      </div>
    );
  }

  return (
    <ReportEngine
      datasets={datasets}
      relationships={relationships}
      dataModelId={dataModelId}
      functions={functions}
      engineRef={engineRef}
      onImport={handleImport}
      onExport={handleExport}
      onSaveReport={handleSaveReport}
      onFontRequest={fetchFonts}
      extraActions={
        <Button icon={<PrinterOutlined />} onClick={handlePrintConfig}>
          打印配置
        </Button>
      }
    />
  );
};

export default EnginePage;
