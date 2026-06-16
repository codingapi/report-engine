import React, { useState, useCallback, useRef, useMemo } from 'react';
import { Button, Upload, Tooltip, Drawer, Badge, message } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import {
  ExportOutlined, ImportOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined,
  BlockOutlined,
} from '@ant-design/icons';
import { Group, Panel as ResizablePanel, Separator, usePanelRef } from 'react-resizable-panels';
import type { CellProp, FieldDropInfo, CellHandle, LoopBlockConfig, CellRange, MenuGroupDef } from '@coding-report/report-univer';
import DataModelPanel from './components/data-model';
import SheetPanel from './components/sheet-panel';
import type { SheetPanelHandle, SheetCellSelectInfo } from './components/sheet-panel';
import PropertyPanel from './components/property-panel/index';
import LoopBlockManager from './components/property-panel/loop-block-manager';
import type { ReportEngineProps, CellBinding, LoopBlock, SummaryRow, Dataset, ReportParam, ReportConfig } from './types';
import type { TemplatePreset } from './types';
import { genId } from './types';
import { valueDisplayText, summaryCellText } from './value-text';
import './index.css';

export interface ReportEngineHandle {
  applyTemplate: (template: TemplatePreset) => void;
  /** 加载报表配置，整体恢复设计态 */
  loadReportConfig: (config: ReportConfig) => void;
}

/**
 * 报表设计器：三栏布局（数据模型 + 电子表格 + 属性面板）。
 *
 * 模板预设、快捷操作等自定义内容由 app 层通过 title (ReactNode) 或 engineRef 控制。
 */
export const ReportEngine: React.FC<ReportEngineProps & {
  engineRef?: React.Ref<ReportEngineHandle>;
}> = ({
  datasets,
  relationships = [],
  dataModelId,
  functions,
  title,
  engineRef,
  onExport,
  onImport,
  onSaveReport,
  onFontRequest,
}) => {
  const sheetRef = useRef<SheetPanelHandle>(null);
  const [messageApi, messageContextHolder] = message.useMessage();

  // ─── 状态 ───
  const [selectedCell, setSelectedCell] = useState<SheetCellSelectInfo | null>(null);
  const [cellBindings, setCellBindings] = useState<CellBinding[]>([]);
  const [loopBlocks, setLoopBlocks] = useState<LoopBlock[]>([]);
  const [summaries, setSummaries] = useState<SummaryRow[]>([]);
  const [params, setParams] = useState<ReportParam[]>([]);
  const [reportId, setReportId] = useState<string | null>(null);
  const [savingReport, setSavingReport] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [activeTemplate, setActiveTemplate] = useState<string | null>(null);
  const lastAppliedRef = useRef<TemplatePreset | null>(null);

  // ─── 面板收缩 ───
  const leftPanelRef = usePanelRef();
  const rightPanelRef = usePanelRef();
  const [leftCollapsed, setLeftCollapsed] = useState(false);
  const [rightCollapsed, setRightCollapsed] = useState(false);
  const [loopDrawerOpen, setLoopDrawerOpen] = useState(false);

  // ─── 清空旧模板单元格 ───
  const clearPreviousTemplate = useCallback((sheetId: string) => {
    const prev = lastAppliedRef.current;
    if (!prev) return;
    // 清空旧模板设置过的单元格（标题 + 表头 + 数据区域 + 汇总行）
    const prevSummaries = prev.summaries || [];
    const maxRow = Math.max(...prev.bindings.map((b) => {
      const parts = b.cellKey.split(':');
      return parseInt(parts[1], 10);
    }), ...prev.cellValues.map((cv) => cv.row), ...prevSummaries.map((s) => s.row));
    const maxCol = Math.max(...prev.bindings.map((b) => {
      const parts = b.cellKey.split(':');
      return parseInt(parts[2], 10);
    }), ...prev.cellValues.map((cv) => cv.col),
      ...prevSummaries.flatMap((s) => s.cells.map((c) => c.column)));
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

    // 回写所有绑定的显示文本，让数据区字段/聚合配置在表格中可见
    for (const b of remappedBindings) {
      const [, r, c] = b.cellKey.split(':');
      sheetRef.current?.setCellValue(
        sheetId,
        parseInt(r, 10),
        parseInt(c, 10),
        valueDisplayText(b.value, datasets, remappedLoops),
      );
    }

    // 回写汇总行（小计/总计）单元格的文本/聚合摘要，使其在表格中可见
    for (const s of tpl.summaries || []) {
      for (const cell of s.cells) {
        sheetRef.current?.setCellValue(sheetId, s.row, cell.column, summaryCellText(cell, datasets));
      }
    }

    setCellBindings(remappedBindings);
    setLoopBlocks(remappedLoops);
    setSummaries(tpl.summaries || []);
    lastAppliedRef.current = tpl;

    messageApi.success(`已加载模板：${tpl.label}`);
  }, [messageApi, clearPreviousTemplate, datasets]);

  // ─── 加载报表配置（整体恢复设计态） ───
  const loadReportConfig = useCallback((config: ReportConfig) => {
    // 清除当前选中状态，避免 loadSnapshot 后旧 sheetId 的残留选中导致面板显示异常
    setSelectedCell(null);

    if (config.template) sheetRef.current?.loadSnapshot(config.template);

    // 获取 Univer 活动工作表的实际 ID（可能是 UUID，不一定是 "sheet1"）
    const actualSheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';

    // 将 cellKey/parentCell 中的 sheet ID 重映射为实际 ID
    const remapKey = (key: string | null) => {
      if (!key) return null;
      const parts = key.split(':');
      return `${actualSheetId}:${parts[1]}:${parts[2]}`;
    };

    const configLoops = config.loopBlocks || [];
    const remappedLoops = configLoops.map((lb) => ({ ...lb, sheetId: actualSheetId }));

    const remappedBindings = (config.cellBindings || []).map((b) => ({
      ...b,
      cellKey: remapKey(b.cellKey)!,
      parentCell: remapKey(b.parentCell),
    }));

    setReportId(config.id ?? null);
    setCellBindings(remappedBindings);
    setLoopBlocks(remappedLoops);
    setSummaries(config.summaries || []);
    setParams(config.params || []);
    setActiveTemplate(null);
    lastAppliedRef.current = null;

    // 回写所有绑定的显示文本（数据区字段/聚合配置在表格中可见）
    const ds = datasets;
    for (const b of remappedBindings) {
      const [, r, c] = b.cellKey.split(':');
      sheetRef.current?.setCellValue(
        actualSheetId,
        parseInt(r, 10),
        parseInt(c, 10),
        valueDisplayText(b.value, ds, remappedLoops),
      );
    }

    messageApi.success(`已打开报表：${config.name || '未命名'}`);
  }, [messageApi, datasets]);

  // ─── 保存报表配置 ───
  const handleSaveReport = useCallback(async () => {
    if (!onSaveReport) return;
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) {
      messageApi.warning('表格为空，无法保存');
      return;
    }
    setSavingReport(true);
    try {
      const config: ReportConfig = {
        id: reportId ?? undefined,
        name: '未命名报表',
        dataModelId,
        cellBindings,
        loopBlocks,
        summaries,
        params,
        template: snapshot,
      };
      const id = await onSaveReport(config);
      if (id) setReportId(id);
      messageApi.success('报表已保存');
    } catch (e) {
      messageApi.error(`保存失败: ${e}`);
    } finally {
      setSavingReport(false);
    }
  }, [onSaveReport, reportId, dataModelId, cellBindings, loopBlocks, summaries, params, messageApi]);

  // 暴露 ref
  React.useImperativeHandle(engineRef, () => ({ applyTemplate, loadReportConfig }), [applyTemplate, loadReportConfig]);

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

  // ─── 配置单元格高亮：非纯文本绑定 + 汇总聚合格 ───
  const highlightCells = useMemo<CellRange[]>(() => {
    const ranges: CellRange[] = [];
    for (const b of cellBindings) {
      // 纯文本（Literal）就是普通文字，不高亮
      if (b.value.type === 'Literal') continue;
      const [sheetId, r, c] = b.cellKey.split(':');
      const row = parseInt(r, 10);
      const col = parseInt(c, 10);
      ranges.push({ sheetId, startRow: row, startColumn: col, endRow: row, endColumn: col });
    }
    // 汇总行：仅聚合格高亮（label 文本格视同普通文字）
    const sumSheet = cellBindings[0]?.cellKey.split(':')[0]
      || sheetRef.current?.getActiveSheetId() || 'sheet1';
    for (const s of summaries) {
      for (const cell of s.cells) {
        // 聚合格、或含 ${} 占位的标签格（如 ${group}小计）都属于配置项
        const isExpr = cell.kind === 'agg' || /\$\{[^}]*\}/.test(cell.payload || '');
        if (!isExpr) continue;
        ranges.push({ sheetId: sumSheet, startRow: s.row, startColumn: cell.column, endRow: s.row, endColumn: cell.column });
      }
    }
    return ranges;
  }, [cellBindings, summaries]);

  // ─── 单元格选中 ───
  const handleCellSelect = useCallback((info: SheetCellSelectInfo) => {
    setSelectedCell(info);
  }, []);

  // ─── 将绑定值回写为单元格显示文本（设计态占位） ───
  const writeBindingText = useCallback(
    (cellKey: string, binding: CellBinding) => {
      const parts = cellKey.split(':');
      if (parts.length !== 3) return;
      const [sheetId, row, col] = parts;
      sheetRef.current?.setCellValue(
        sheetId,
        parseInt(row, 10),
        parseInt(col, 10),
        valueDisplayText(binding.value, datasets, loopBlocks),
      );
    },
    [datasets, loopBlocks],
  );

  // ─── 属性面板回调 ───
  const handleBindingChange = useCallback(
    (cellKey: string, newBinding: CellBinding) => {
      setCellBindings((prev) =>
        prev.map((b) => (b.cellKey === cellKey ? newBinding : b)),
      );
      writeBindingText(cellKey, newBinding);
      setActiveTemplate(null);
    },
    [writeBindingText],
  );

  const handleBindingCreate = useCallback((cellKey: string) => {
    const defaultBinding: CellBinding = {
      cellKey,
      value: { type: 'FieldValue', payload: '' },
      expansion: 'VERTICAL',
      expandMode: 'LIST',
      mergeRepeated: false,
      parentCell: null,
      conditions: [],
    };
    setCellBindings((prev) => [
      ...prev.filter((b) => b.cellKey !== cellKey),
      defaultBinding,
    ]);
    setActiveTemplate(null);
  }, []);

  const handleBindingDelete = useCallback((cellKey: string) => {
    setCellBindings((prev) => prev.filter((b) => b.cellKey !== cellKey));
    setActiveTemplate(null);
  }, []);

  // ─── 汇总行回调（小计/总计） ───
  const handleSummaryRowCreate = useCallback((row: number) => {
    setSummaries((prev) => [...prev, { id: genId(), row, groupBy: null, cells: [] }]);
    setActiveTemplate(null);
  }, []);

  const handleSummaryRowChange = useCallback(
    (id: string, newRow: SummaryRow) => {
      const sheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';
      setSummaries((prev) => {
        const oldRow = prev.find((s) => s.id === id);
        // 清空被移除的列
        if (oldRow) {
          const keep = new Set(newRow.cells.map((c) => c.column));
          for (const c of oldRow.cells) {
            if (!keep.has(c.column)) sheetRef.current?.setCellValue(sheetId, oldRow.row, c.column, '');
          }
        }
        // 回写当前列文本/聚合摘要
        for (const c of newRow.cells) {
          sheetRef.current?.setCellValue(sheetId, newRow.row, c.column, summaryCellText(c, datasets));
        }
        return prev.map((s) => (s.id === id ? newRow : s));
      });
      setActiveTemplate(null);
    },
    [datasets],
  );

  const handleSummaryRowDelete = useCallback((id: string) => {
    const sheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';
    setSummaries((prev) => {
      const row = prev.find((s) => s.id === id);
      if (row) for (const c of row.cells) sheetRef.current?.setCellValue(sheetId, row.row, c.column, '');
      return prev.filter((s) => s.id !== id);
    });
    setActiveTemplate(null);
  }, []);

  // ─── 右键：将选中区域设为循环块 ───
  const handleCreateLoopFromRange = useCallback(
    (range: CellRange) => {
      setLoopBlocks((prev) => [
        ...prev,
        {
          id: genId(),
          label: `循环块 ${prev.length + 1}`,
          sheetId: range.sheetId,
          startRow: range.startRow,
          startColumn: range.startColumn,
          endRow: range.endRow,
          endColumn: range.endColumn,
          source: { datasetId: datasets[0]?.id || '', filters: [], groupBy: [], orderBy: [] },
        },
      ]);
      setLoopDrawerOpen(true);
      setActiveTemplate(null);
      messageApi.success('已创建循环块，请在抽屉中配置驱动数据集');
    },
    [datasets, messageApi],
  );

  const contextMenuGroups = useMemo<MenuGroupDef[]>(
    () => [
      {
        id: 'report-engine-menu',
        title: '报表',
        items: [
          {
            id: 'set-loop-block',
            title: '设为循环块',
            tooltip: '将选中区域设为循环渲染块',
            onClick: handleCreateLoopFromRange,
          },
        ],
      },
    ],
    [handleCreateLoopFromRange],
  );

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
      // 导出时附带表达式预览（友好文本），随配置一起存储到后端
      const bindingsOut = cellBindings.map((b) => ({
        ...b,
        preview: valueDisplayText(b.value, datasets, loopBlocks),
      }));
      const summariesOut = summaries.map((s) => ({
        ...s,
        cells: s.cells.map((c) => ({ ...c, preview: summaryCellText(c, datasets) })),
      }));
      await onExport(bindingsOut, loopBlocks, summariesOut, snapshot, params);
      messageApi.success('导出成功');
    } catch (e) {
      messageApi.error(`导出失败: ${e}`);
    } finally {
      setExporting(false);
    }
  }, [onExport, cellBindings, loopBlocks, summaries, params, datasets, messageApi]);

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
        <div className="re-header__title">{title ?? '报表设计器'}</div>
        <div className="re-header__actions">
          {onSaveReport && (
            <Button
              icon={<SaveOutlined />}
              loading={savingReport}
              onClick={handleSaveReport}
            >
              保存
            </Button>
          )}
          <Badge count={loopBlocks.length} size="small" offset={[-4, 0]}>
            <Button icon={<BlockOutlined />} onClick={() => setLoopDrawerOpen(true)}>
              循环块
            </Button>
          </Badge>
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

      {/* 三栏主体 */}
      <div className="re-body">
        <Group orientation="horizontal">
          <ResizablePanel
            id="left-panel"
            panelRef={leftPanelRef}
            defaultSize="20%"
            minSize="280px"
            maxSize="35%"
            collapsible
            collapsedSize="36px"
            onResize={(_size, _id, _prev) => {
              setLeftCollapsed(leftPanelRef.current?.isCollapsed() ?? false);
            }}
          >
            {leftCollapsed ? (
              <div className="re-panel-collapsed">
                <Tooltip title="展开数据模型" placement="right">
                  <Button
                    type="text"
                    icon={<MenuUnfoldOutlined />}
                    onClick={() => leftPanelRef.current?.expand()}
                  />
                </Tooltip>
              </div>
            ) : (
              <div className="re-panel-wrapper">
                <div className="re-panel-collapse-btn">
                  <Tooltip title="收起">
                    <Button
                      type="text"
                      size="small"
                      icon={<MenuFoldOutlined />}
                      onClick={() => leftPanelRef.current?.collapse()}
                    />
                  </Tooltip>
                </div>
                <DataModelPanel
                  datasets={datasets}
                  relationships={relationships}
                  params={params}
                  onParamsChange={setParams}
                />
              </div>
            )}
          </ResizablePanel>

          <Separator className="re-separator" />

          <ResizablePanel id="center-panel" defaultSize="70%">
            <SheetPanel
              ref={sheetRef}
              cellProps={cellProps}
              loopBlocks={loopBlockConfigs}
              highlightCells={highlightCells}
              contextMenuGroups={contextMenuGroups}
              onCellSelect={handleCellSelect}
              onFieldDrop={handleFieldDrop}
              onFontRequest={onFontRequest}
              onReady={() => console.log('[ReportEngine] Univer ready, sheetId:', sheetRef.current?.getActiveSheetId())}
            />
          </ResizablePanel>

          <Separator className="re-separator" />

          <ResizablePanel
            id="right-panel"
            panelRef={rightPanelRef}
            defaultSize="15%"
            minSize="220px"
            maxSize="25%"
            collapsible
            collapsedSize="36px"
            onResize={(_size, _id, _prev) => {
              setRightCollapsed(rightPanelRef.current?.isCollapsed() ?? false);
            }}
          >
            {rightCollapsed ? (
              <div className="re-panel-collapsed">
                <Tooltip title="展开属性面板" placement="left">
                  <Button
                    type="text"
                    icon={<MenuFoldOutlined />}
                    onClick={() => rightPanelRef.current?.expand()}
                  />
                </Tooltip>
              </div>
            ) : (
              <div className="re-panel-wrapper">
                <div className="re-panel-collapse-btn re-panel-collapse-btn--left">
                  <Tooltip title="收起">
                    <Button
                      type="text"
                      size="small"
                      icon={<MenuUnfoldOutlined />}
                      onClick={() => rightPanelRef.current?.collapse()}
                    />
                  </Tooltip>
                </div>
                <PropertyPanel
                  selectedCell={selectedCell}
                  cellBindings={cellBindings}
                  summaries={summaries}
                  loopBlocks={loopBlocks}
                  datasets={datasets}
                  params={params}
                  functions={functions}
                  onBindingChange={handleBindingChange}
                  onBindingCreate={handleBindingCreate}
                  onBindingDelete={handleBindingDelete}
                  onSummaryRowChange={handleSummaryRowChange}
                  onSummaryRowCreate={handleSummaryRowCreate}
                  onSummaryRowDelete={handleSummaryRowDelete}
                />
              </div>
            )}
          </ResizablePanel>
        </Group>
      </div>

      {/* 循环块管理抽屉 */}
      <Drawer
        title="循环块管理"
        extra={<span style={{ fontSize: 12, color: '#999', fontWeight: 'normal' }}>定义模板中需要重复渲染的区域</span>}
        placement="right"
        width={520}
        open={loopDrawerOpen}
        onClose={() => setLoopDrawerOpen(false)}
        destroyOnClose={false}
      >
        <LoopBlockManager
          loopBlocks={loopBlocks}
          datasets={datasets}
          onChange={setLoopBlocks}
        />
      </Drawer>
    </div>
  );
};
