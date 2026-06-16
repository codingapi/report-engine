import { useEffect, useRef, useState } from 'react';
import { Spin, Button, Space, Tooltip, Select } from 'antd';
import { FileTextOutlined, FolderOpenOutlined } from '@ant-design/icons';
import { ReportEngine } from '@coding-report/report-engine';
import type { ReportEngineHandle, TemplatePreset, ReportConfig } from '@coding-report/report-engine';
import type { Dataset, CellBinding, LoopBlock, SummaryRow, ReportParam, Relationship } from '@coding-report/report-engine';
import { ALL_TEMPLATES } from './templates';
import {
  importExcel, fetchFonts, fetchDataModel, renderReport, fetchFunctions,
  saveReportConfig, loadReportConfig, listReportConfigs,
} from '@coding-report/report-api';
import type { RenderBindingDTO, RenderValueDTO, ExpressionCatalog, ReportBrief } from '@coding-report/report-api';
import type { ExcelWorkbook } from '@coding-report/report-univer';

// ─── 转换函数 ──────────────────────────────────

function toValueDTO(value: any): RenderValueDTO {
  return {
    type: value.type,
    payload: value.payload,
    aggregation: value.aggregation,
    operand: value.operand ? toValueDTO(value.operand) : undefined,
    funcName: value.funcName,
    args: value.args?.map(toValueDTO),
    parts: value.parts?.map((p: any) => ({
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
    preview: binding.preview,
  };
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

// ─── 标题区域（含快捷模板） ────────────────────

function TitleBar({
  templates,
  activeTemplate,
  onApply,
  reports,
  onOpenReport,
  onRefreshReports,
}: {
  templates: TemplatePreset[];
  activeTemplate: string | null;
  onApply: (tpl: TemplatePreset) => void;
  reports: ReportBrief[];
  onOpenReport: (id: string) => void;
  onRefreshReports: () => void;
}) {
  return (
    <>
      <span>报表设计器</span>
      <Select
        size="small"
        placeholder="打开报表"
        suffixIcon={<FolderOpenOutlined />}
        style={{ width: 160, marginLeft: 12 }}
        value={null}
        onDropdownVisibleChange={(open) => open && onRefreshReports()}
        onChange={onOpenReport}
        options={reports.map((r) => ({ value: r.id, label: r.name }))}
        notFoundContent="暂无已保存报表"
      />
      {templates.length > 0 && (
        <Space size={4} wrap style={{ marginLeft: 12 }}>
          <span style={{ fontSize: 13, color: '#666', fontWeight: 'normal' }}>
            <FileTextOutlined /> 快速模板：
          </span>
          {templates.map((tpl) => (
            <Tooltip key={tpl.id} title={tpl.description}>
              <Button
                size="small"
                type={activeTemplate === tpl.id ? 'primary' : 'default'}
                onClick={() => onApply(tpl)}
              >
                {tpl.label}
              </Button>
            </Tooltip>
          ))}
        </Space>
      )}
    </>
  );
}

// ─── 页面组件 ──────────────────────────────────

const EnginePage = () => {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [relationships, setRelationships] = useState<Relationship[]>([]);
  const [functions, setFunctions] = useState<ExpressionCatalog>();
  const [loading, setLoading] = useState(true);
  const [activeTemplate, setActiveTemplate] = useState<string | null>(null);
  const [reports, setReports] = useState<ReportBrief[]>([]);
  const engineRef = useRef<ReportEngineHandle>(null);

  useEffect(() => {
    fetchFunctions()
      .then(setFunctions)
      .catch((e) => console.error('加载公式列表失败:', e));
  }, []);

  useEffect(() => {
    fetchDataModel()
      .then((dm) => {
        setDatasets(
          dm.datasets.map((d) => ({
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
        setRelationships(
          dm.relationships.map((r) => ({
            left: r.left,
            right: r.right,
            joinType: r.joinType as Relationship['joinType'],
          })),
        );
      })
      .catch((e) => console.error('加载数据模型失败:', e))
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

  const handleApplyTemplate = (tpl: TemplatePreset) => {
    engineRef.current?.applyTemplate(tpl);
    setActiveTemplate(tpl.id);
  };

  const refreshReports = () => {
    listReportConfigs().then(setReports).catch((e) => console.error('加载报表列表失败:', e));
  };

  const handleOpenReport = async (id: string) => {
    try {
      const config = await loadReportConfig(id);
      engineRef.current?.loadReportConfig(config as unknown as ReportConfig);
      setActiveTemplate(null);
    } catch (e) {
      console.error('打开报表失败:', e);
    }
  };

  const handleSaveReport = async (config: ReportConfig): Promise<string> => {
    return saveReportConfig(config as unknown as Record<string, unknown>);
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
      relationships={relationships}
      functions={functions}
      title={
        <TitleBar
          templates={ALL_TEMPLATES}
          activeTemplate={activeTemplate}
          onApply={handleApplyTemplate}
          reports={reports}
          onOpenReport={handleOpenReport}
          onRefreshReports={refreshReports}
        />
      }
      engineRef={engineRef}
      onImport={handleImport}
      onExport={handleExport}
      onSaveReport={handleSaveReport}
      onFontRequest={fetchFonts}
    />
  );
};

export default EnginePage;
