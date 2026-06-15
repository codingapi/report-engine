import React, { useState, useCallback, useRef } from 'react';
import { Button, Upload, Space, Tooltip, message } from 'antd';
import { ExportOutlined, ImportOutlined, FileTextOutlined } from '@ant-design/icons';
import { Group, Panel as ResizablePanel } from 'react-resizable-panels';
import type { CellProp, FieldDropInfo, CellHandle, LoopBlockConfig } from '@coding-report/report-univer';
import DatasetTree from './components/dataset-tree';
import SheetPanel from './components/sheet-panel';
import type { SheetPanelHandle, SheetCellSelectInfo } from './components/sheet-panel';
import PropertyPanel from './components/property-panel';
import type { ReportEngineProps, CellBinding, LoopBlock, SummaryRow, Dataset } from './types';
import type { TemplatePreset } from './templates';
import './index.css';

export interface ReportEngineHandle {
  applyTemplate: (template: TemplatePreset) => void;
}

/**
 * 报表设计器：三栏布局（数据集树 + 电子表格 + 属性面板）。
 */
export const ReportEngine: React.FC<ReportEngineProps & {
  templates?: TemplatePreset[];
  engineRef?: React.Ref<ReportEngineHandle>;
}> = ({
  datasets,
  title = '报表设计器',
  templates,
  engineRef,
  onExport,
  onImport,
  onFontRequest,
}) => {
  const sheetRef = useRef<SheetPanelHandle>(null);
  const [messageApi, messageContextHolder] = message.useMessage();

  // ─── 状态 ───
  const [selectedCell, setSelectedCell] = useState<SheetCellSelectInfo | null>(null);
  const [cellBindings, setCellBindings] = useState<CellBinding[]>([]);
  const [loopBlocks, setLoopBlocks] = useState<LoopBlock[]>([]);
  const [summaries, setSummaries] = useState<SummaryRow[]>([]);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [activeTemplate, setActiveTemplate] = useState<string | null>(null);
  const lastAppliedRef = useRef<TemplatePreset | null>(null);

  // ─── 清空旧模板单元格 ───
  const clearPreviousTemplate = useCallback((sheetId: string) => {
    const prev = lastAppliedRef.current;
    if (!prev) return;
    // 清空旧模板设置过的单元格（标题 + 表头 + 数据区域）
    const maxRow = Math.max(...prev.bindings.map((b) => {
      const parts = b.cellKey.split(':');
      return parseInt(parts[1], 10);
    }), ...prev.cellValues.map((cv) => cv.row));
    const maxCol = Math.max(...prev.bindings.map((b) => {
      const parts = b.cellKey.split(':');
      return parseInt(parts[2], 10);
    }), ...prev.cellValues.map((cv) => cv.col));
    // 清空 0..maxRow × 0..maxCol 区域
    for (let r = 0; r <= maxRow; r++) {
      for (let c = 0; c <= maxCol; c++) {
        sheetRef.current?.setCellValue(sheetId, r, c, '');
      }
    }
  }, []);

  // ─── 应用模板 ───
  const applyTemplate = useCallback((tpl: TemplatePreset) => {
    setActiveTemplate(tpl.id);

    // 获取实际 sheet ID（Univer 默认 ID 可能不是 'sheet1'）
    const sheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';
    console.log('[ReportEngine] applyTemplate:', tpl.id, 'sheetId:', sheetId);

    // 清空旧模板的单元格内容
    clearPreviousTemplate(sheetId);

    // 设置单元格显示文本
    for (const cv of tpl.cellValues) {
      sheetRef.current?.setCellValue(sheetId, cv.row, cv.col, cv.text);
    }

    // 将模板中的 cellKey/parentCell 替换为实际 sheet ID
    const remapKey = (key: string | null) => {
      if (!key) return null;
      const parts = key.split(':');
      return `${sheetId}:${parts[1]}:${parts[2]}`;
    };

    const remappedBindings = tpl.bindings.map((b) => ({
      ...b,
      cellKey: remapKey(b.cellKey)!,
      parentCell: remapKey(b.parentCell),
      conditions: b.conditions.map((c) => ({
        ...c,
        left: c.left,
        right: c.right,
      })),
    }));

    const remappedLoops = (tpl.loopBlocks || []).map((lb) => ({
      ...lb,
      sheetId,
    }));

    setCellBindings(remappedBindings);
    setLoopBlocks(remappedLoops);
    setSummaries(tpl.summaries || []);
    lastAppliedRef.current = tpl;

    messageApi.success(`已加载模板：${tpl.label}`);
  }, [messageApi, clearPreviousTemplate]);

  // 暴露 ref
  React.useImperativeHandle(engineRef, () => ({ applyTemplate }), [applyTemplate]);

  // ─── cellProps：CellBinding → Univer CellProp 映射 ───
  const cellProps: Record<string, CellProp[]> = {};
  for (const b of cellBindings) {
    cellProps[b.cellKey] = [{
      kind: 'cellBinding',
      field: b.value.payload,
      data: {
        valueType: b.value.type,
        expansion: b.expansion,
        expandMode: b.expandMode,
        mergeRepeated: b.mergeRepeated,
        parentCell: b.parentCell,
        conditionsCount: b.conditions.length,
      },
    }];
  }

  // loopBlockConfigs: LoopBlock → LoopBlockConfig 映射
  const loopBlockConfigs: Record<string, LoopBlockConfig> = {};
  for (const lb of loopBlocks) {
    loopBlockConfigs[lb.id] = {
      id: lb.id,
      sheetId: lb.sheetId,
      startRow: lb.startRow,
      startColumn: lb.startColumn,
      endRow: lb.endRow,
      endColumn: lb.endColumn,
      label: lb.label,
    };
  }

  // ─── 单元格选中 ───
  const handleCellSelect = useCallback((info: SheetCellSelectInfo) => {
    setSelectedCell(info);
  }, []);

  // ─── 字段拖入 → 创建 CellBinding ───
  const handleFieldDrop = useCallback(
    (info: FieldDropInfo, handle: CellHandle) => {
      try {
        const data = JSON.parse(info.data);
        if (data.datasetId && data.field) {
          const ck = `${info.sheetId}:${info.row}:${info.column}`;
          const fieldRef = `${data.datasetId}.${data.field}`;

          handle.setValue(data.alias || data.field);

          const binding: CellBinding = {
            cellKey: ck,
            value: { type: 'FieldValue', payload: fieldRef },
            expansion: 'VERTICAL',
            expandMode: 'LIST',
            mergeRepeated: false,
            parentCell: null,
            conditions: [],
          };

          setCellBindings((prev) => {
            const filtered = prev.filter((b) => b.cellKey !== ck);
            return [...filtered, binding];
          });
          setActiveTemplate(null);

          messageApi.success(`已绑定 ${data.alias || data.field}`);
        }
      } catch {
        // 非 JSON 拖拽数据，忽略
      }
    },
    [messageApi],
  );

  // ─── 导出 ───
  const handleExport = useCallback(async () => {
    if (!onExport) return;
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) {
      messageApi.warning('表格为空，无法导出');
      return;
    }
    setExporting(true);
    try {
      await onExport(cellBindings, loopBlocks, summaries, snapshot);
      messageApi.success('导出成功');
    } catch (e) {
      messageApi.error(`导出失败: ${e}`);
    } finally {
      setExporting(false);
    }
  }, [onExport, cellBindings, loopBlocks, summaries, messageApi]);

  // ─── 导入 ───
  const handleImport = useCallback(
    async (file: File) => {
      if (!onImport) return;
      setImporting(true);
      try {
        const snapshot = await onImport(file);
        sheetRef.current?.loadSnapshot(snapshot);
        messageApi.success('导入成功');
      } catch (e) {
        messageApi.error(`导入失败: ${e}`);
      } finally {
        setImporting(false);
      }
    },
    [onImport, messageApi],
  );

  return (
    <div className="re">
      {messageContextHolder}

      {/* 顶栏 */}
      <div className="re-header">
        <div className="re-header__title">{title}</div>
        <div className="re-header__actions">
          {onImport && (
            <Upload
              accept=".xlsx,.xls"
              showUploadList={false}
              beforeUpload={(file) => {
                handleImport(file);
                return false;
              }}
            >
              <Button icon={<ImportOutlined />} loading={importing}>
                导入模板
              </Button>
            </Upload>
          )}
          {onExport && (
            <Button
              type="primary"
              icon={<ExportOutlined />}
              loading={exporting}
              onClick={handleExport}
            >
              导出报表
            </Button>
          )}
        </div>
      </div>

      {/* 模板栏 */}
      {templates && templates.length > 0 && (
        <div className="re-template-bar">
          <Space size={4} wrap>
            <span className="re-template-bar__label">
              <FileTextOutlined /> 快速模板：
            </span>
            {templates.map((tpl) => (
              <Tooltip key={tpl.id} title={tpl.description}>
                <Button
                  size="small"
                  type={activeTemplate === tpl.id ? 'primary' : 'default'}
                  onClick={() => applyTemplate(tpl)}
                >
                  {tpl.label}
                </Button>
              </Tooltip>
            ))}
          </Space>
        </div>
      )}

      {/* 三栏主体 */}
      <div className="re-body">
        <Group orientation="horizontal">
          <ResizablePanel defaultSize="15%" minSize="200px" maxSize="25%">
            <DatasetTree datasets={datasets} />
          </ResizablePanel>

          <ResizablePanel defaultSize="70%">
            <SheetPanel
              ref={sheetRef}
              cellProps={cellProps}
              loopBlocks={loopBlockConfigs}
              onCellSelect={handleCellSelect}
              onFieldDrop={handleFieldDrop}
              onFontRequest={onFontRequest}
              onReady={() => console.log('[ReportEngine] Univer ready, sheetId:', sheetRef.current?.getActiveSheetId())}
            />
          </ResizablePanel>

          <ResizablePanel defaultSize="15%" minSize="200px" maxSize="25%">
            <PropertyPanel
              selectedCell={selectedCell}
              cellBindings={cellBindings}
              datasets={datasets}
            />
          </ResizablePanel>
        </Group>
      </div>
    </div>
  );
};
