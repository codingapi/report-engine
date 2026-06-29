import React, { useState, useCallback, useRef, useMemo } from 'react';
import { Button, Upload, Tooltip, Drawer, Divider, message } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import {
  ExportOutlined,
  ImportOutlined,
  EyeOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BlockOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { Group, Panel as ResizablePanel, Separator, usePanelRef } from 'react-resizable-panels';
import type {
  CellProp,
  FieldDropInfo,
  CellHandle,
  LoopBlockConfig,
  CellRange,
  MenuGroupDef,
  RowsColsChange,
  UndoRedoPhase,
} from '@coding-report/report-univer';
import DataModelPanel from './components/data-model';
import SheetPanel from './components/sheet-panel';
import type { SheetPanelHandle, SheetCellSelectInfo } from './components/sheet-panel';
import PropertyPanel from './components/property-panel/index';
import LoopBlockManager from './components/property-panel/loop-block-manager';
import type {
  ReportEngineProps,
  CellBinding,
  LoopBlock,
  SummaryRow,
  SummaryCell,
  ParamDTO,
  ReportDTO,
  ReportValue,
  RenderConfig,
} from './types';
import type { TemplatePreset } from './types';
import { genId } from './types';
import { parseCellKey, makeCellKey } from './utils/excel-cell';
import { summaryAxis, summaryCellRC, summaryHit, crossPosOf } from './utils/summary-axis';
import { useReportIO } from './hooks/use-report-io';
import ReportPreview from './components/preview/preview';
import type { ReportPreviewHandle } from './components/preview/preview';
import { valueDisplayText, parseTemplate } from './value-text';

/** config 三态快照（行列删/插的撤销重做用，与 Univer undo 栈对齐） */
interface ConfigSnapshot {
  cellBindings: CellBinding[];
  loopBlocks: LoopBlock[];
  summaries: SummaryRow[];
}

/**
 * 按行/列删除或插入，对 config 三态做坐标位移（纯函数，不改原对象）。
 * 删除范围内的记录被丢弃；范围后反向位移 count；插入点后正向位移 count。
 */
function shiftConfig(
  snapshot: ConfigSnapshot,
  change: { dimension: 'row' | 'col'; action: 'remove' | 'insert'; start: number; count: number },
): ConfigSnapshot {
  const { dimension, action, start, count } = change;
  const end = start + count - 1;

  // 单个索引位移：返回新值；删除范围内返回 null（该记录应丢弃）
  const shiftIndex = (idx: number): number | null => {
    if (action === 'insert') return idx >= start ? idx + count : idx;
    if (idx < start) return idx;
    if (idx > end) return idx - count;
    return null;
  };

  const cellBindings: CellBinding[] = [];
  for (const b of snapshot.cellBindings) {
    const { sheetId, row, col } = parseCellKey(b.cellKey);
    const target = dimension === 'row' ? row : col;
    const moved = shiftIndex(target);
    if (moved === null) continue; // 整格被删，丢弃绑定
    const newRow = dimension === 'row' ? moved : row;
    const newCol = dimension === 'col' ? moved : col;
    // parentCell 同步位移（落在删除范围内则断开为 null）
    let parentCell = b.parentCell;
    if (parentCell) {
      const p = parseCellKey(parentCell);
      const pTarget = dimension === 'row' ? p.row : p.col;
      const pMoved = shiftIndex(pTarget);
      parentCell =
        pMoved === null
          ? null
          : makeCellKey(
              p.sheetId,
              dimension === 'row' ? pMoved : p.row,
              dimension === 'col' ? pMoved : p.col,
            );
    }
    cellBindings.push({ ...b, cellKey: makeCellKey(sheetId, newRow, newCol), parentCell });
  }

  const loopBlocks: LoopBlock[] = [];
  for (const lb of snapshot.loopBlocks) {
    const s = dimension === 'row' ? lb.startRow : lb.startColumn;
    const e = dimension === 'row' ? lb.endRow : lb.endColumn;
    const ms = shiftIndex(s);
    const me = shiftIndex(e);
    // 起止任一端落入删除范围 → 块结构被破坏，丢弃整块（避免残留半块）
    if (ms === null || me === null) continue;
    loopBlocks.push(
      dimension === 'row'
        ? { ...lb, startRow: ms, endRow: me }
        : { ...lb, startColumn: ms, endColumn: me },
    );
  }

  const summaries: SummaryRow[] = [];
  for (const s of snapshot.summaries) {
    const axis = s.axis ?? 'VERTICAL';
    // 汇总主轴：纵向汇总锚定在某行(mainPos=行)、交叉区间是列；横向反之。
    const mainIsRow = axis === 'VERTICAL';
    let mainPos = s.mainPos;
    if ((mainIsRow && dimension === 'row') || (!mainIsRow && dimension === 'col')) {
      const m = shiftIndex(s.mainPos);
      if (m === null) continue; // 汇总锚定行/列被删，丢弃整条
      mainPos = m;
    }
    let cells = s.cells;
    let crossFrom = s.crossFrom;
    let crossTo = s.crossTo;
    const crossIsRow = !mainIsRow;
    if ((crossIsRow && dimension === 'row') || (!crossIsRow && dimension === 'col')) {
      cells = [];
      for (const c of s.cells) {
        const moved = shiftIndex(c.crossPos);
        if (moved === null) continue;
        cells.push({ ...c, crossPos: moved });
      }
      const cf = shiftIndex(s.crossFrom);
      const ct = shiftIndex(s.crossTo);
      crossFrom = cf ?? Math.max(start, 0);
      crossTo = ct ?? Math.max(start - 1, 0);
    }
    summaries.push({ ...s, mainPos, crossFrom, crossTo, cells });
  }

  return { cellBindings, loopBlocks, summaries };
}

/** 旧格式 SummaryCell（kind/payload/aggregation）→ 新格式（value: ReportValue） */
interface LegacySummaryCell {
  crossPos: number;
  value?: unknown;
  kind?: 'label' | 'agg';
  payload?: string;
  aggregation?: string;
}

/** 兼容旧持久化数据的 SummaryRow（cells 可能是旧格式） */
interface LegacySummaryRow {
  id: string;
  axis?: SummaryRow['axis'];
  mainPos: number;
  crossFrom: number;
  crossTo: number;
  groupBy: { datasetId: string; field: string } | null;
  cells: LegacySummaryCell[];
}

function migrateSummaryCell(cell: LegacySummaryCell): SummaryCell {
  if (cell.value) return cell as unknown as SummaryCell; // 已是新格式
  if (cell.kind === 'label') {
    return { crossPos: cell.crossPos, value: parseTemplate(cell.payload || '') };
  }
  // kind === 'agg'
  return {
    crossPos: cell.crossPos,
    value: {
      type: 'Aggregate',
      aggregation: (cell.aggregation || 'SUM') as ReportValue['aggregation'],
      operand: { type: 'FieldValue', payload: cell.payload || '' },
    },
  };
}
import './index.css';

export interface ReportEngineHandle {
  applyTemplate: (template: TemplatePreset) => void;
  /** 加载报表配置，整体恢复设计态 */
  loadReportConfig: (config: ReportDTO) => void;
  /** 获取当前完整报表配置（外部调试/持久化用），表格为空时返回 null */
  getReportConfig: () => ReportDTO | null;
}

/**
 * 报表设计器：三栏布局（数据模型 + 电子表格 + 属性面板）。
 *
 * 模板预设、快捷操作等自定义内容由 app 层通过 title (ReactNode) 或 engineRef 控制。
 */
export const ReportEngine: React.FC<
  ReportEngineProps & {
    engineRef?: React.Ref<ReportEngineHandle>;
  }
> = ({
  datasets,
  relationships = [],
  transforms = [],
  dataModelId,
  functions,
  title,
  engineRef,
  renderService,
  onImport,
  onSaveReport,
  onFontRequest,
  extraActions,
  customActions,
  enableImport = true,
  enableLoopBlock = true,
  enablePreview = true,
  enableExport = true,
  enableSave = true,
}) => {
  const sheetRef = useRef<SheetPanelHandle>(null);
  const [messageApi, messageContextHolder] = message.useMessage();

  // ─── 状态 ───
  const [selectedCell, setSelectedCell] = useState<SheetCellSelectInfo | null>(null);
  const [cellBindings, setCellBindings] = useState<CellBinding[]>([]);
  const [loopBlocks, setLoopBlocks] = useState<LoopBlock[]>([]);
  const [summaries, setSummaries] = useState<SummaryRow[]>([]);
  const [params, setParams] = useState<ParamDTO[]>([]);
  const [reportId, setReportId] = useState<string | null>(null);
  const [reportName, setReportName] = useState<string>('未命名报表');
  const [_activeTemplate, setActiveTemplate] = useState<string | null>(null);

  // ─── 撤销/重做对齐：config 是独立于 Univer 的平行存储，需自建快照栈与之对齐 ───
  // 渲染期同步镜像最新三态（用户操作间必有渲染，故 undo 触发时 ref 已是最新提交值）
  const cellBindingsRef = useRef(cellBindings);
  cellBindingsRef.current = cellBindings;
  const loopBlocksRef = useRef(loopBlocks);
  loopBlocksRef.current = loopBlocks;
  const summariesRef = useRef(summaries);
  summariesRef.current = summaries;
  // 行列删/插的配置快照栈（与 Univer undo/redo 对齐）
  const undoConfigStackRef = useRef<ConfigSnapshot[]>([]);
  const redoConfigStackRef = useRef<ConfigSnapshot[]>([]);
  // 清除/删除内容的墓碑：cellKey → 被移除的绑定；撤销回写原内容时据此恢复
  const clearedBindingsRef = useRef<Map<string, CellBinding>>(new Map());
  // 当前撤销/重做阶段（由 report-univer 透出）
  const undoRedoPhaseRef = useRef<UndoRedoPhase>('normal');
  const handleUndoRedoStateChange = useCallback((phase: UndoRedoPhase) => {
    undoRedoPhaseRef.current = phase;
  }, []);
  const lastAppliedRef = useRef<TemplatePreset | null>(null);

  // ─── 面板收缩 ───
  const leftPanelRef = usePanelRef();
  const rightPanelRef = usePanelRef();
  const [leftCollapsed, setLeftCollapsed] = useState(false);
  const [rightCollapsed, setRightCollapsed] = useState(false);
  const [loopDrawerOpen, setLoopDrawerOpen] = useState(false);

  // ─── 报表 IO（保存/导入/收集渲染入参） ───
  const { savingReport, importing, collectRenderArgs, handleSaveReport, handleImport } =
    useReportIO({
      sheetRef,
      datasets,
      dataModelId,
      cellBindings,
      loopBlocks,
      summaries,
      params,
      reportId,
      reportName,
      onReportIdChange: setReportId,
      onSaveReport,
      onImport,
      messageApi,
    });

  // ─── 预览/导出能力（注入 renderService 后启用） ───
  const previewFlowRef = useRef<ReportPreviewHandle>(null);
  const [previewConfig, setPreviewConfig] = useState<RenderConfig | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const buildRenderConfig = () => {
    const args = collectRenderArgs();
    if (!args) return null;
    return {
      dataModelId,
      bindings: args.bindingsOut,
      loops: loopBlocks,
      summaries: args.summariesOut,
      workbook: args.templateOut,
      params,
    } as RenderConfig;
  };
  const handlePreviewClick = () => {
    const cfg = buildRenderConfig();
    if (!cfg) {
      messageApi.warning('表格为空，无法预览');
      return;
    }
    setPreviewConfig(cfg);
  };
  const handleExportClick = () => {
    const cfg = buildRenderConfig();
    if (!cfg) {
      messageApi.warning('表格为空，无法导出');
      return;
    }
    previewFlowRef.current?.exportXlsx(cfg);
  };

  // ─── 清空旧模板单元格 ───
  const clearPreviousTemplate = useCallback((sheetId: string) => {
    const prev = lastAppliedRef.current;
    if (!prev) return;
    // 清空旧模板设置过的单元格（标题 + 表头 + 数据区域 + 汇总行）
    const prevSummaries = prev.summaries || [];
    const summaryRC = prevSummaries.flatMap((s) =>
      s.cells.map((c) => summaryCellRC(s, c.crossPos)),
    );
    const maxRow = Math.max(
      ...prev.bindings.map((b) => parseCellKey(b.cellKey).row),
      ...prev.cellValues.map((cv) => cv.row),
      ...summaryRC.map((rc) => rc.row),
    );
    const maxCol = Math.max(
      ...prev.bindings.map((b) => parseCellKey(b.cellKey).col),
      ...prev.cellValues.map((cv) => cv.col),
      ...summaryRC.map((rc) => rc.col),
    );
    // 清空 0..maxRow × 0..maxCol 区域
    for (let r = 0; r <= maxRow; r++) {
      for (let c = 0; c <= maxCol; c++) {
        sheetRef.current?.setCellValue(sheetId, r, c, '');
      }
    }
  }, []);

  // ─── 应用模板 ───
  const applyTemplate = useCallback(
    (tpl: TemplatePreset) => {
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
        const { row, col } = parseCellKey(key);
        return makeCellKey(sheetId, row, col);
      };

      const remappedLoops = (tpl.loopBlocks || []).map((lb) => ({
        ...lb,
        sheetId,
      }));

      // 绑定附带 displayText（别名展示文本）：既写进单元格供显示，也作为回声判别基准
      const remappedBindings = tpl.bindings.map((b) => {
        const value = b.value;
        return {
          ...b,
          cellKey: remapKey(b.cellKey)!,
          parentCell: remapKey(b.parentCell),
          conditions: b.conditions.map((c) => ({ ...c, left: c.left, right: c.right })),
          displayText: valueDisplayText(value, datasets, remappedLoops, params),
        };
      });

      const remappedSummaries = (tpl.summaries || []).map((s) => ({
        ...s,
        cells: s.cells.map((cell) => ({
          ...cell,
          displayText: valueDisplayText(cell.value, datasets, remappedLoops, params),
        })),
      }));

      // 回写显示文本（与 displayText 一致 → handleCellValueChange 比对后判为回声，跳过）
      for (const b of remappedBindings) {
        const { row, col } = parseCellKey(b.cellKey);
        sheetRef.current?.setCellValue(sheetId, row, col, b.displayText ?? '');
      }
      for (const s of remappedSummaries) {
        for (const cell of s.cells) {
          const { row, col } = summaryCellRC(s, cell.crossPos);
          sheetRef.current?.setCellValue(sheetId, row, col, cell.displayText ?? '');
        }
      }

      setCellBindings(remappedBindings);
      setLoopBlocks(remappedLoops);
      setSummaries(remappedSummaries);
      lastAppliedRef.current = tpl;

      messageApi.success(`已加载模板：${tpl.label}`);
    },
    [messageApi, clearPreviousTemplate, datasets],
  );

  // ─── 加载报表配置（整体恢复设计态） ───
  const loadReportConfig = useCallback(
    (config: ReportDTO) => {
      // 清除当前选中状态，避免 loadSnapshot 后旧 sheetId 的残留选中导致面板显示异常
      setSelectedCell(null);

      if (config.template) sheetRef.current?.loadSnapshot(config.template);

      // 获取 Univer 活动工作表的实际 ID（可能是 UUID，不一定是 "sheet1"）
      const actualSheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';

      // 将 cellKey/parentCell 中的 sheet ID 重映射为实际 ID
      const remapKey = (key: string | null) => {
        if (!key) return null;
        const { row, col } = parseCellKey(key);
        return makeCellKey(actualSheetId, row, col);
      };

      const configLoops = config.loopBlocks || [];
      const remappedLoops = configLoops.map((lb) => ({ ...lb, sheetId: actualSheetId }));
      const loadedParams = config.params || [];
      const ds = datasets;

      // 绑定附带 displayText（别名展示文本）：写进单元格供显示 + 回声判别基准
      const remappedBindings = (config.cellBindings || []).map((b) => ({
        ...b,
        cellKey: remapKey(b.cellKey)!,
        parentCell: remapKey(b.parentCell),
        displayText: valueDisplayText(b.value, ds, remappedLoops, loadedParams),
      }));

      // 迁移汇总行：旧格式（kind/payload）→ 新格式（value: ReportValue），并附带 displayText
      const migratedSummaries: SummaryRow[] = (config.summaries || []).map(
        (s: LegacySummaryRow) => ({
          ...s,
          cells: (s.cells || []).map((c) => {
            const cell = migrateSummaryCell(c);
            return {
              ...cell,
              displayText: valueDisplayText(cell.value, ds, remappedLoops, loadedParams),
            };
          }),
        }),
      );

      setReportId(config.id ?? null);
      setReportName(config.name || '未命名报表');
      setCellBindings(remappedBindings);
      setLoopBlocks(remappedLoops);
      setSummaries(migratedSummaries);
      setParams(loadedParams);
      setActiveTemplate(null);
      lastAppliedRef.current = null;

      // 回写显示文本（与 displayText 一致 → handleCellValueChange 判为回声，跳过）
      for (const b of remappedBindings) {
        const { row, col } = parseCellKey(b.cellKey);
        sheetRef.current?.setCellValue(actualSheetId, row, col, b.displayText ?? '');
      }
      for (const s of migratedSummaries) {
        for (const c of s.cells) {
          const { row, col } = summaryCellRC(s, c.crossPos);
          sheetRef.current?.setCellValue(actualSheetId, row, col, c.displayText ?? '');
        }
      }

      messageApi.success(`已打开报表：${config.name || '未命名'}`);
    },
    [messageApi, datasets],
  );

  // 获取当前完整报表配置（外部按需调用）
  const getReportConfig = useCallback((): ReportDTO | null => {
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) return null;
    return {
      id: reportId ?? undefined,
      name: reportName,
      dataModelId,
      // displayText 为设计态 transient 字段，对外配置剥离
      cellBindings: cellBindings.map(({ displayText: _dt, ...b }) => b),
      loopBlocks,
      summaries: summaries.map((s) => ({
        ...s,
        cells: s.cells.map(({ displayText: _dt, ...c }) => c),
      })),
      params,
      template: snapshot,
    };
  }, [reportId, reportName, dataModelId, cellBindings, loopBlocks, summaries, params]);

  // 暴露 ref
  React.useImperativeHandle(
    engineRef,
    () => ({ applyTemplate, loadReportConfig, getReportConfig }),
    [applyTemplate, loadReportConfig, getReportConfig],
  );

  // ─── cellProps：CellBinding → Univer CellProp 映射 ───
  const cellProps: Record<string, CellProp[]> = {};
  for (const b of cellBindings) {
    cellProps[b.cellKey] = [
      {
        kind: 'cellBinding',
        field: b.value.payload,
        data: {
          valueType: b.value.type,
          expansion: b.expansion,
          expandMode: b.expandMode,
          mergeRepeated: b.mergeRepeated,
          parentCell: b.parentCell,
          independent: b.independent ?? false,
          conditionsCount: b.conditions.length,
        },
      },
    ];
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
      const { sheetId, row, col } = parseCellKey(b.cellKey);
      ranges.push({ sheetId, startRow: row, startColumn: col, endRow: row, endColumn: col });
    }
    // 汇总行：仅聚合格高亮（label 文本格视同普通文字）
    const sumSheet =
      (cellBindings[0] ? parseCellKey(cellBindings[0].cellKey).sheetId : undefined) ||
      sheetRef.current?.getActiveSheetId() ||
      'sheet1';
    for (const s of summaries) {
      for (const cell of s.cells) {
        // 聚合格、Template（含 ${} 占位）都属于配置项，纯 Literal 文本不高亮
        const isExpr = cell.value.type !== 'Literal';
        if (!isExpr) continue;
        const { row, col } = summaryCellRC(s, cell.crossPos);
        ranges.push({
          sheetId: sumSheet,
          startRow: row,
          startColumn: col,
          endRow: row,
          endColumn: col,
        });
      }
    }
    return ranges;
  }, [cellBindings, summaries]);

  // ─── 单元格选中 ───
  const handleCellSelect = useCallback((info: SheetCellSelectInfo) => {
    setSelectedCell(info);
  }, []);

  // ─── 属性面板回调 ───
  // 改绑定 → 算出新 displayText 一并写进 binding（回声基准）+ 回写单元格（写的就是 displayText，故被判为回声跳过）
  const handleBindingChange = useCallback(
    (cellKey: string, newBinding: CellBinding) => {
      const displayText = valueDisplayText(newBinding.value, datasets, loopBlocks, params);
      const withText = { ...newBinding, displayText };
      setCellBindings((prev) => prev.map((b) => (b.cellKey === cellKey ? withText : b)));
      if (cellKey.split(':').length === 3) {
        const { sheetId, row, col } = parseCellKey(cellKey);
        sheetRef.current?.setCellValue(sheetId, row, col, displayText);
      }
      setActiveTemplate(null);
    },
    [datasets, loopBlocks, params],
  );

  const handleBindingCreate = useCallback((cellKey: string) => {
    const defaultBinding: CellBinding = {
      cellKey,
      value: { type: 'Literal', payload: '' },
      expansion: 'VERTICAL',
      expandMode: 'LIST',
      mergeRepeated: false,
      parentCell: null,
      conditions: [],
      displayText: '',
    };
    setCellBindings((prev) => [...prev.filter((b) => b.cellKey !== cellKey), defaultBinding]);
    setActiveTemplate(null);
  }, []);

  const handleBindingDelete = useCallback((cellKey: string) => {
    setCellBindings((prev) => prev.filter((b) => b.cellKey !== cellKey));
    setActiveTemplate(null);
  }, []);

  // ─── 汇总行回调（小计/总计） ───
  const handleSummaryRowChange = useCallback(
    (id: string, newRow: SummaryRow) => {
      const sheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';
      setSummaries((prev) => {
        const oldRow = prev.find((s) => s.id === id);
        // 清空被移除的格
        if (oldRow) {
          const keep = new Set(newRow.cells.map((c) => c.crossPos));
          for (const c of oldRow.cells) {
            if (!keep.has(c.crossPos)) {
              const { row, col } = summaryCellRC(oldRow, c.crossPos);
              sheetRef.current?.setCellValue(sheetId, row, col, '');
            }
          }
        }
        // 每个汇总格附带 displayText（回声基准）+ 回写单元格
        const cellsWithText = newRow.cells.map((c) => ({
          ...c,
          displayText: valueDisplayText(c.value, datasets, loopBlocks, params),
        }));
        for (const c of cellsWithText) {
          const { row, col } = summaryCellRC(newRow, c.crossPos);
          sheetRef.current?.setCellValue(sheetId, row, col, c.displayText);
        }
        const rowWithText = { ...newRow, cells: cellsWithText };
        return prev.map((s) => (s.id === id ? rowWithText : s));
      });
      setActiveTemplate(null);
    },
    [datasets, loopBlocks, params],
  );

  const handleSummaryRowDelete = useCallback((id: string) => {
    const sheetId = sheetRef.current?.getActiveSheetId() || 'sheet1';
    setSummaries((prev) => {
      const summary = prev.find((s) => s.id === id);
      if (summary)
        for (const c of summary.cells) {
          const { row, col } = summaryCellRC(summary, c.crossPos);
          sheetRef.current?.setCellValue(sheetId, row, col, '');
        }
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

  // ─── 右键：将选中区域设为汇总（按选区形状自动判轴） ───
  //   横向选区（同一行）→ 纵向汇总（带下方追加合计行）；纵向选区（同一列）→ 横向汇总（带右侧追加合计列）。
  const handleCreateSummaryFromRange = useCallback(
    (range: CellRange) => {
      const sameRow = range.startRow === range.endRow;
      const sameCol = range.startColumn === range.endColumn;
      if (sameRow === sameCol) {
        // 单格(都true)或斜向块(都false)：无法判定方向
        messageApi.warning('汇总需选择同一行（→纵向合计行）或同一列（→横向合计列）内的连续单元格');
        return;
      }
      const newSummary: SummaryRow = sameRow
        ? {
            id: genId(),
            axis: 'VERTICAL',
            mainPos: range.startRow,
            crossFrom: range.startColumn,
            crossTo: range.endColumn,
            groupBy: null,
            cells: [],
          }
        : {
            id: genId(),
            axis: 'HORIZONTAL',
            mainPos: range.startColumn,
            crossFrom: range.startRow,
            crossTo: range.endRow,
            groupBy: null,
            cells: [],
          };
      setSummaries((prev) => [...prev, newSummary]);
      setActiveTemplate(null);
      messageApi.success(
        sameRow
          ? '已创建纵向汇总（下方合计行），请在右侧属性面板逐列配置标签/聚合'
          : '已创建横向汇总（右侧合计列），请在右侧属性面板逐行配置标签/聚合',
      );
    },
    [messageApi],
  );

  const contextMenuGroups = useMemo<MenuGroupDef[]>(
    () => [
      {
        id: 'report-engine-menu',
        title: '报表配置',
        items: [
          {
            id: 'set-loop-block',
            title: '设为循环块',
            tooltip: '将选中区域设为循环渲染块',
            onClick: handleCreateLoopFromRange,
          },
          {
            id: 'set-summary-row',
            title: '设为汇总',
            tooltip:
              '框选行段（同一行）→ 纵向汇总（下方合计行）；框选列段（同一列）→ 横向汇总（右侧合计列）',
            onClick: handleCreateSummaryFromRange,
          },
        ],
      },
    ],
    [handleCreateLoopFromRange, handleCreateSummaryFromRange],
  );

  // ─── 字段/参数拖入 → 创建 CellBinding ───
  const handleFieldDrop = useCallback(
    (info: FieldDropInfo, handle: CellHandle) => {
      try {
        const data = JSON.parse(info.data);
        const ck = `${info.sheetId}:${info.row}:${info.column}`;

        // 参数拖入
        if (data.type === 'report-param') {
          const paramAlias = data.paramAlias || data.paramName;
          const value: ReportValue = { type: 'NameRef', payload: data.paramName };
          // displayText 走统一的 valueDisplayText，与其它路径一致（回声基准）
          const displayText = valueDisplayText(value, datasets, loopBlocks, params);
          handle.setValue(displayText);

          const binding: CellBinding = {
            cellKey: ck,
            value,
            expansion: 'NONE',
            expandMode: 'LIST',
            mergeRepeated: false,
            parentCell: null,
            conditions: [],
            displayText,
          };

          setCellBindings((prev) => {
            const filtered = prev.filter((b) => b.cellKey !== ck);
            return [...filtered, binding];
          });
          setActiveTemplate(null);

          messageApi.success(`已绑定参数 ${paramAlias}`);
          return;
        }

        // 字段拖入
        if (data.datasetId && data.field) {
          const fieldRef = `${data.datasetId}.${data.field}`;
          const fieldAlias = data.alias || data.field;
          const value: ReportValue = { type: 'FieldValue', payload: fieldRef };
          const displayText = valueDisplayText(value, datasets, loopBlocks, params);
          handle.setValue(displayText);

          const binding: CellBinding = {
            cellKey: ck,
            value,
            expansion: 'VERTICAL',
            expandMode: 'LIST',
            mergeRepeated: false,
            parentCell: null,
            conditions: [],
            displayText,
          };

          setCellBindings((prev) => {
            const filtered = prev.filter((b) => b.cellKey !== ck);
            return [...filtered, binding];
          });
          setActiveTemplate(null);

          messageApi.success(`已绑定 ${fieldAlias}`);
        }
      } catch {
        // 非 JSON 拖拽数据，忽略
      }
    },
    [messageApi, datasets, loopBlocks, params],
  );

  // ─── 单元格值变更 → 回声判别（程序回写 vs 用户手敲） ───
  // 用 displayText 作基准区分两类来源，替代时序型 isLoadingRef：
  //   新文本 === binding.displayText  ⇒ 程序回写（加载/属性面板/拖拽）产生的回声 → 忽略
  //   新文本 === ''                   ⇒ 用户清空 → 移除绑定
  //   其它                            ⇒ 用户手敲 → 纯文本/模板格退化为 Literal；引用格保护不覆盖
  // 手敲一律按字面文本处理，绝不把别名反解回字段引用（消除别名重名歧义）。
  const handleCellValueChange = useCallback(
    (changes: Array<{ sheetId: string; row: number; col: number; value: string }>) => {
      // 同步普通 CellBinding
      setCellBindings((prev) => {
        let changed = false;
        const next: CellBinding[] = [];
        const consumed = new Set<string>(); // 已处理的 cellKey（避免重复恢复）
        for (const b of prev) {
          const { sheetId, row, col } = parseCellKey(b.cellKey);
          consumed.add(b.cellKey);
          const change = changes.find(
            (c) => c.sheetId === sheetId && c.row === row && c.col === col,
          );
          if (!change || change.value === (b.displayText ?? '')) {
            next.push(b); // 无变更 / 回声 → 保持
            continue;
          }
          if (change.value === '') {
            changed = true; // 用户清空 → 移除并入墓碑（供撤销恢复）
            clearedBindingsRef.current.set(b.cellKey, b);
            continue;
          }
          // 用户手敲
          if (b.value.type === 'Literal' || b.value.type === 'Template') {
            changed = true;
            next.push({
              ...b,
              value: { type: 'Literal', payload: change.value },
              displayText: change.value,
            });
          } else {
            next.push(b); // 引用格（字段/聚合/参数）保护：改表达式请走属性面板
          }
        }
        // 撤销恢复：内容被写回且匹配墓碑绑定的 displayText → 还原原绑定（仅撤销/重做期，避免手敲同文本误恢复）
        if (undoRedoPhaseRef.current !== 'normal' && clearedBindingsRef.current.size > 0) {
          for (const c of changes) {
            if (!c.value) continue;
            const key = makeCellKey(c.sheetId, c.row, c.col);
            if (consumed.has(key)) continue;
            const tomb = clearedBindingsRef.current.get(key);
            if (tomb && (tomb.displayText ?? '') === c.value) {
              next.push(tomb);
              clearedBindingsRef.current.delete(key);
              consumed.add(key);
              changed = true;
            }
          }
        }
        return changed ? next : prev;
      });

      // 同步汇总行 SummaryCell
      setSummaries((prev) => {
        let changed = false;
        const next = prev.map((s) => {
          const axis = summaryAxis(s);
          const matching = changes.filter((c) => summaryHit(s, c.row, c.col));
          if (matching.length === 0) return s;
          let cellsChanged = false;
          const cells = [...s.cells];
          for (const c of matching) {
            const cross = crossPosOf(axis, c.row, c.col);
            const idx = cells.findIndex((sc) => sc.crossPos === cross);
            const existing = idx >= 0 ? cells[idx] : null;
            if (existing && c.value === (existing.displayText ?? '')) continue; // 回声
            if (c.value === '') {
              if (idx >= 0) {
                cells.splice(idx, 1);
                cellsChanged = true;
              } // 清空 → 移除
              continue;
            }
            // 用户手敲
            if (existing) {
              if (existing.value.type === 'Literal' || existing.value.type === 'Template') {
                cells[idx] = {
                  ...existing,
                  value: { type: 'Literal', payload: c.value },
                  displayText: c.value,
                };
                cellsChanged = true;
              } // 聚合等引用格保护
            } else {
              cells.push({
                crossPos: cross,
                value: { type: 'Literal', payload: c.value },
                displayText: c.value,
              });
              cellsChanged = true;
            }
          }
          if (cellsChanged) {
            changed = true;
            return { ...s, cells };
          }
          return s;
        });
        return changed ? next : prev;
      });
    },
    [],
  );

  // ─── 选区清除（清除内容/格式/全部）→ 移除对应绑定 ───
  // 被移除的绑定存入墓碑（按 cellKey），撤销时 Univer 把原内容写回 → handleCellValueChange 据 displayText 恢复
  const handleSelectionClear = useCallback((cellKeys: string[]) => {
    const keys = new Set(cellKeys);
    // 提取 row:col 用于匹配汇总行单元格（汇总行无 sheetId）
    const rowColKeys = new Set(
      cellKeys.map((k) => {
        const parts = k.split(':');
        return `${parts[1]}:${parts[2]}`;
      }),
    );
    setCellBindings((prev) => {
      const next: CellBinding[] = [];
      for (const b of prev) {
        if (keys.has(b.cellKey)) {
          clearedBindingsRef.current.set(b.cellKey, b); // 入墓碑（供撤销恢复）
        } else {
          next.push(b);
        }
      }
      return next;
    });
    setSummaries((prev) =>
      prev.map((s) => ({
        ...s,
        cells: s.cells.filter((c) => {
          const { row, col } = summaryCellRC(s, c.crossPos);
          return !rowColKeys.has(`${row}:${col}`);
        }),
      })),
    );
  }, []);

  // ─── 行/列删除/插入 → 同步按坐标记录的属性（带撤销/重做对齐） ───
  // 表格删/插整行整列后，Univer 内容已位移，但 cellBindings/loopBlocks/summaries 仍按旧坐标记录。
  // 删除会丢弃落在删除范围内的记录 → 该数据只能靠快照恢复，故维护与 Univer undo 对齐的配置快照栈：
  //   normal → 操作前压栈 before，再做位移；undo → 弹 before 直接恢复；redo → 重压并重做位移。
  const handleRowsColsChanged = useCallback((change: RowsColsChange) => {
    const { phase } = change;

    if (phase === 'undo') {
      // 撤销：Univer 重放反向 mutation（删→插/插→删），此处不按表格推导，直接恢复操作前快照
      const before = undoConfigStackRef.current.pop();
      if (!before) return;
      redoConfigStackRef.current.push({
        cellBindings: cellBindingsRef.current,
        loopBlocks: loopBlocksRef.current,
        summaries: summariesRef.current,
      });
      setCellBindings(before.cellBindings);
      setLoopBlocks(before.loopBlocks);
      setSummaries(before.summaries);
      setActiveTemplate(null);
      return;
    }

    // normal / redo：先压 before 快照（redo 从 redo 栈取回的就是当时的 before），再做位移
    const before: ConfigSnapshot = {
      cellBindings: cellBindingsRef.current,
      loopBlocks: loopBlocksRef.current,
      summaries: summariesRef.current,
    };
    undoConfigStackRef.current.push(before);
    if (phase === 'normal') {
      redoConfigStackRef.current = []; // 新操作清空 redo 栈
    }

    const after = shiftConfig(before, change);
    setCellBindings(after.cellBindings);
    setLoopBlocks(after.loopBlocks);
    setSummaries(after.summaries);
    setActiveTemplate(null);
  }, []);

  return (
    <div className="re">
      {messageContextHolder}

      {/* 顶栏 */}
      <div className="re-header">
        <div className="re-header__title">{title ?? reportName}</div>
        <div className="re-header__actions">
          {customActions}
          {customActions && <Divider type="vertical" />}
          {enableImport && onImport && (
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
          {enableLoopBlock && (
            <Button icon={<BlockOutlined />} onClick={() => setLoopDrawerOpen(true)}>
              循环块{loopBlocks.length > 0 && `(${loopBlocks.length})`}
            </Button>
          )}
          {enablePreview && renderService && (
            <Button icon={<EyeOutlined />} loading={previewing} onClick={handlePreviewClick}>
              报表预览
            </Button>
          )}
          {enableExport && renderService && (
            <Button icon={<ExportOutlined />} loading={exporting} onClick={handleExportClick}>
              导出报表
            </Button>
          )}
          {enableSave && onSaveReport && (
            <Button icon={<SaveOutlined />} loading={savingReport} onClick={handleSaveReport}>
              保存报表
            </Button>
          )}
          {extraActions}
        </div>
      </div>

      {/* 三栏主体 */}
      <div className="re-body">
        <Group orientation="horizontal">
          <ResizablePanel
            id="left-panel"
            panelRef={leftPanelRef}
            defaultSize="25%"
            minSize="320px"
            maxSize="40%"
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
              onCellValueChange={handleCellValueChange}
              onSelectionClear={handleSelectionClear}
              onRowsColsChanged={handleRowsColsChanged}
              onUndoRedoStateChange={handleUndoRedoStateChange}
              onFontRequest={onFontRequest}
              onReady={() =>
                console.log(
                  '[ReportEngine] Univer ready, sheetId:',
                  sheetRef.current?.getActiveSheetId(),
                )
              }
            />
          </ResizablePanel>

          <Separator className="re-separator" />

          <ResizablePanel
            id="right-panel"
            panelRef={rightPanelRef}
            defaultSize="30%"
            minSize="360px"
            maxSize="40%"
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
              <PropertyPanel
                selectedCell={selectedCell}
                cellBindings={cellBindings}
                summaries={summaries}
                loopBlocks={loopBlocks}
                datasets={datasets}
                params={params}
                transforms={transforms}
                functions={functions}
                onBindingChange={handleBindingChange}
                onBindingCreate={handleBindingCreate}
                onBindingDelete={handleBindingDelete}
                onSummaryRowChange={handleSummaryRowChange}
                onSummaryRowDelete={handleSummaryRowDelete}
                onCollapse={() => rightPanelRef.current?.collapse()}
              />
            )}
          </ResizablePanel>
        </Group>
      </div>

      {/* 循环块管理抽屉 */}
      <Drawer
        className="re-drawer"
        title={
          <span>
            循环块管理
            <Tooltip title="循环块用于定义模板中需要重复渲染的区域，按驱动数据集的行数（或分组数）复制多次。块内单元格通过「循环字段」引用当前迭代行的字段。新增循环块请在表格中选中区域，右键选择「设为循环块」。">
              <QuestionCircleOutlined
                style={{
                  marginLeft: 6,
                  color: 'rgba(0,0,0,0.45)',
                  cursor: 'pointer',
                  fontSize: 14,
                }}
              />
            </Tooltip>
          </span>
        }
        placement="right"
        styles={{ wrapper: { width: 520 } }}
        open={loopDrawerOpen}
        onClose={() => setLoopDrawerOpen(false)}
        destroyOnHidden={false}
      >
        <LoopBlockManager
          loopBlocks={loopBlocks}
          datasets={datasets}
          params={params}
          onChange={setLoopBlocks}
        />
      </Drawer>

      {/* 预览/导出能力（参数弹窗 + 预览抽屉 + 反查明细） */}
      {renderService && (
        <ReportPreview
          ref={previewFlowRef}
          renderService={renderService}
          config={previewConfig}
          onPreviewingChange={setPreviewing}
          onExportingChange={setExporting}
        />
      )}
    </div>
  );
};
