import { useEffect, useRef, useState } from 'react';
import { Spin, Button, Space, Tooltip } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import { ReportEngine } from '@coding-report/report-engine';
import type { ReportEngineHandle, TemplatePreset } from '@coding-report/report-engine';
import type { Dataset, CellBinding, LoopBlock, SummaryRow } from '@coding-report/report-engine';
import { ALL_TEMPLATES } from './templates';
import { importExcel, fetchFonts, fetchDatasets, renderReport } from '@coding-report/report-api';
import type { RenderBindingDTO, RenderValueDTO } from '@coding-report/report-api';
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
}: {
  templates: TemplatePreset[];
  activeTemplate: string | null;
  onApply: (tpl: TemplatePreset) => void;
}) {
  return (
    <>
      <span>报表设计器</span>
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
  const [loading, setLoading] = useState(true);
  const [activeTemplate, setActiveTemplate] = useState<string | null>(null);
  const engineRef = useRef<ReportEngineHandle>(null);

  useEffect(() => {
    fetchDatasets()
      .then((list) => {
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
    const blob = await renderReport({
      cellBindings: bindings.map(toBindingDTO),
      loopBlocks: loops,
      summaries: summaries,
      template: workbook,
    });
    downloadBlob(blob, `report-${new Date().toISOString().slice(0, 10)}.xlsx`);
  };

  const handleApplyTemplate = (tpl: TemplatePreset) => {
    engineRef.current?.applyTemplate(tpl);
    setActiveTemplate(tpl.id);
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
      title={
        <TitleBar
          templates={ALL_TEMPLATES}
          activeTemplate={activeTemplate}
          onApply={handleApplyTemplate}
        />
      }
      engineRef={engineRef}
      onImport={handleImport}
      onExport={handleExport}
      onFontRequest={fetchFonts}
    />
  );
};

export default EnginePage;
