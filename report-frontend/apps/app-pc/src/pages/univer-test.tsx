/**
 * Univer 技术验证页面
 *
 * 验证目标：
 * 1. 手动 plugins 方式初始化 Univer（不使用 preset），去除自带公式插件
 * 2. 获取工作表快照数据（单元格内容 + 样式 + 合并信息 + 边框 + 行列尺寸）
 * 3. 循环块高亮覆盖（highlight API，不修改单元格样式）
 * 4. 单元格属性绑定（字段绑定 / 数据配置 / 显示格式 / 聚合计算）
 */

import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import { Button, Tag, Space, Select, Input, Empty, Divider } from 'antd';
import { DatabaseOutlined, HighlightOutlined, DeleteOutlined, PlusOutlined, PlayCircleOutlined } from '@ant-design/icons';

// Univer 核心
import { createUniver, LocaleType, mergeLocales } from '@univerjs/presets';
import type { FUniver } from '@univerjs/presets';

// 基础插件
import { UniverNetworkPlugin } from '@univerjs/network';
import { UniverDocsPlugin } from '@univerjs/docs';
import { UniverRenderEnginePlugin } from '@univerjs/engine-render';
import { UniverUIPlugin } from '@univerjs/ui';
import { UniverDocsUIPlugin } from '@univerjs/docs-ui';

// 公式引擎（单元格编辑依赖其数据管道）
import { UniverFormulaEnginePlugin } from '@univerjs/engine-formula';

// 电子表格核心
import { UniverSheetsPlugin } from '@univerjs/sheets';
import { UniverSheetsUIPlugin } from '@univerjs/sheets-ui';

// 数字格式
import { UniverSheetsNumfmtPlugin } from '@univerjs/sheets-numfmt';
import { UniverSheetsNumfmtUIPlugin } from '@univerjs/sheets-numfmt-ui';

// 公式与表格交互（单元格编辑依赖，必须保留）
import { UniverSheetsFormulaPlugin } from '@univerjs/sheets-formula';
import { UniverSheetsFormulaUIPlugin } from '@univerjs/sheets-formula-ui';

// 中文 locale
import UniverUIZhCN from '@univerjs/ui/locale/zh-CN';
import UniverDocsUIZhCN from '@univerjs/docs-ui/locale/zh-CN';
import UniverSheetsZhCN from '@univerjs/sheets/locale/zh-CN';
import UniverSheetsUIZhCN from '@univerjs/sheets-ui/locale/zh-CN';
import UniverSheetsNumfmtUIZhCN from '@univerjs/sheets-numfmt-ui/locale/zh-CN';
import UniverSheetsFormulaZhCN from '@univerjs/sheets-formula/locale/zh-CN';
import UniverSheetsFormulaUIZhCN from '@univerjs/sheets-formula-ui/locale/zh-CN';

// Facade 模块增强（为 FUniver 扩展方法）
import '@univerjs/sheets/facade';
import '@univerjs/ui/facade';
import '@univerjs/sheets-ui/facade';
import '@univerjs/network/facade';
import '@univerjs/docs-ui/facade';
import '@univerjs/sheets-formula/facade';
import '@univerjs/sheets-formula-ui/facade';

// CSS
import '@univerjs/preset-sheets-core/lib/index.css';

// 数据提取工具
import { extractWorkbookSnapshot, renderWorkbookSnapshot, MOCK_SNAPSHOT } from './univer-test-utils';

// 属性绑定
import type { CellProp, CellPropStore, LoopBlock, PropKindTemplate } from './univer-test-props';
import { PROP_KINDS, PROP_KIND_MAP, makeCellKey, makeMergeKey } from './univer-test-props';

// 数据源（用于字段下拉）
import { mockDataConfig } from '../data/mock-data';

// ─── 常量 ──────────────────────────────────────────────

const VALUE_TYPE_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '字符串', color: 'blue' },
  1: { label: '数字', color: 'green' },
  2: { label: '布尔', color: 'orange' },
};

const HIGHLIGHT_STYLE = {
  fill: 'rgba(22, 119, 255, 0.08)',
  stroke: '#1677ff',
  strokeWidth: 1,
  strokeDash: 8,
  widgets: {
    top: false, bottom: false, left: false, right: false,
    topLeft: false, topRight: false, bottomLeft: false, bottomRight: false,
  },
};

// ─── 字段选项构建 ──────────────────────────────────────

const fieldOptions = mockDataConfig.tables.flatMap((table) =>
  table.fields.map((f) => ({
    label: `${table.alias || table.name}.${f.alias || f.name}`,
    value: `${table.name}.${f.name}`,
  })),
);

// ─── 单元格样式控制 ────────────────────────────────────

/**
 * 通过 Univer Facade API 设置单元格样式
 * 支持链式调用，可在任意时机调用
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function applyCellStyle(api: any, row: number, col: number, style: {
  fontColor?: string;
  background?: string;
  fontSize?: number;
  fontWeight?: 'bold' | 'normal';
}) {
  const ws = api?.getActiveWorkbook?.()?.getActiveSheet?.();
  if (!ws) return;
  const range = ws.getRange(row, col);
  if (!range) return;
  if (style.fontColor) range.setFontColor(style.fontColor);
  if (style.background) range.setBackground(style.background);
  if (style.fontSize) range.setFontSize(style.fontSize);
  if (style.fontWeight) range.setFontWeight(style.fontWeight);
}

function clearCellStyle(api: any, row: number, col: number) {
  const ws = api?.getActiveWorkbook?.()?.getActiveSheet?.();
  if (!ws) return;
  const range = ws.getRange(row, col);
  if (!range) return;
  range.clearFormat();
}

/** 读取单元格当前样式，用于回填编辑器 */
function readCellStyle(api: any, row: number, col: number): {
  fontColor?: string;
  background?: string;
  fontSize?: number;
  bold?: boolean;
} {
  const ws = api?.getActiveWorkbook?.()?.getActiveSheet?.();
  if (!ws) return {};
  const range = ws.getRange(row, col);
  if (!range) return {};
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw = (range as any).getCellStyleData?.('cell');
  if (!raw) return {};
  const result: { fontColor?: string; background?: string; fontSize?: number; bold?: boolean } = {};
  if (raw.cl?.rgb) result.fontColor = raw.cl.rgb;
  if (raw.bg?.rgb) result.background = raw.bg.rgb;
  if (raw.fs) result.fontSize = raw.fs;
  if (raw.bl === 1) result.bold = true;
  return result;
}

// ─── 类型 ──────────────────────────────────────────────

interface CellInfo {
  sheetId: string;
  position: string;
  row: number;
  col: number;
  value: string | number | boolean | null;
  valueType: number | null;
  hasRichText: boolean;
  hasFormula: boolean;
  jsType: string;
  mergeRange: { startRow: number; startColumn: number; endRow: number; endColumn: number } | null;
  /** 当前单元格样式（选中时读取，用于回填样式编辑器） */
  currentStyle: { fontColor?: string; background?: string; fontSize?: number; bold?: boolean };
}

// ─── 辅助 ──────────────────────────────────────────────

let blockIdCounter = 0;
const genBlockId = () => `loop-${++blockIdCounter}-${Date.now().toString(36)}`;

// ─── 组件 ──────────────────────────────────────────────

const UniverTestPage: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const univerAPIRef = useRef<FUniver | null>(null);

  // 单元格选中信息
  const [cellInfo, setCellInfo] = useState<CellInfo | null>(null);

  // 属性存储
  const [propStore, setPropStore] = useState<CellPropStore>({
    cellProps: {},
    mergeProps: {},
    loopBlockProps: {},
  });

  // 循环块
  const [loopBlocks, setLoopBlocks] = useState<LoopBlock[]>([]);
  const loopBlocksRef = useRef<LoopBlock[]>([]);
  loopBlocksRef.current = loopBlocks;
  const highlightDisposablesRef = useRef<Array<{ dispose: () => void }>>([]);

  // 添加属性表单
  const [addingPropKind, setAddingPropKind] = useState<string | null>(null);
  const [newPropField, setNewPropField] = useState<string>('');
  const [newPropData, setNewPropData] = useState<Record<string, string>>({});

  // ─── 高亮管理 ──────────────────────────────────────

  const reapplyHighlights = useCallback(() => {
    const api = univerAPIRef.current;
    const blocks = loopBlocksRef.current;
    if (!api || blocks.length === 0) return;

    highlightDisposablesRef.current.forEach((d) => {
      try { d.dispose(); } catch { /* ignore */ }
    });
    highlightDisposablesRef.current = [];

    const workbook = api.getActiveWorkbook();
    if (!workbook) return;
    const sheet = workbook.getActiveSheet();
    if (!sheet) return;

    const ranges = blocks
      .filter((b) => b.sheetId === sheet.getSheetId())
      .map((b) => sheet.getRange(
        b.startRow, b.startColumn,
        b.endRow - b.startRow + 1, b.endColumn - b.startColumn + 1,
      ));

    if (ranges.length > 0) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const disposable = (sheet as any).highlightRanges(ranges, HIGHLIGHT_STYLE);
      highlightDisposablesRef.current = [disposable];
    }
  }, []);

  // loopBlocks 变化时重新应用高亮
  useEffect(() => {
    reapplyHighlights();
  }, [loopBlocks, reapplyHighlights]);

  // ─── 初始化 ──────────────────────────────────────

  useEffect(() => {
    if (!containerRef.current) return;

    const { univerAPI } = createUniver({
      locale: LocaleType.ZH_CN,
      locales: {
        [LocaleType.ZH_CN]: mergeLocales(
          UniverUIZhCN, UniverDocsUIZhCN, UniverSheetsZhCN,
          UniverSheetsUIZhCN, UniverSheetsNumfmtUIZhCN,
          UniverSheetsFormulaZhCN, UniverSheetsFormulaUIZhCN,
        ),
      },
      presets: [],
      plugins: [
        UniverNetworkPlugin,
        UniverDocsPlugin,
        UniverRenderEnginePlugin,
        [UniverUIPlugin, {
          container: containerRef.current,
          ribbonType: 'simple' as const,
          formulaBar: false,
          menu: {
            'formula-ui.operation.insert-function.common': { hidden: true },
            'formula-ui.operation.insert-function.financial': { hidden: true },
            'formula-ui.operation.insert-function.logical': { hidden: true },
            'formula-ui.operation.insert-function.text': { hidden: true },
            'formula-ui.operation.insert-function.date': { hidden: true },
            'formula-ui.operation.insert-function.lookup': { hidden: true },
            'formula-ui.operation.insert-function.math': { hidden: true },
            'formula-ui.operation.insert-function.statistical': { hidden: true },
            'formula-ui.operation.insert-function.engineering': { hidden: true },
            'formula-ui.operation.insert-function.information': { hidden: true },
            'formula-ui.operation.insert-function.database': { hidden: true },
            'formula-ui.operation.insert-function.more-functions': { hidden: true },
            'sheet.toolbar.text-to-number': { hidden: true },
            'sheet.contextMenu.text-to-number': { hidden: true },
          },
        }],
        UniverDocsUIPlugin,
        UniverFormulaEnginePlugin,
        UniverSheetsPlugin,
        [UniverSheetsUIPlugin, { formulaBar: false }],
        UniverSheetsNumfmtPlugin,
        UniverSheetsNumfmtUIPlugin,
        UniverSheetsFormulaPlugin,
        [UniverSheetsFormulaUIPlugin, { functionScreenTips: false }],
      ],
    });

    univerAPI.createWorkbook({});
    // 扩展列数到 100（默认 20 列只到 T）
    univerAPI.getActiveWorkbook()?.getActiveSheet()?.setColumnCount(100);
    univerAPIRef.current = univerAPI;

    // 监听单元格选中事件
    univerAPI.addEvent(univerAPI.Event.SelectionChanged, (params) => {
      const { worksheet: ws, selections } = params as {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        worksheet: any;
        selections: Array<{ startRow: number; startColumn: number }>;
      };
      if (!selections || selections.length === 0) return;

      const { startRow: row, startColumn: col } = selections[0];
      const fRange = ws.getRange(row, col);
      const value = fRange.getValue();
      const a1 = fRange.getA1Notation();
      const sheetId = ws.getSheetId();

      let valueType: number | null = null;
      let hasRichText = false;
      let hasFormula = false;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const raw = (ws as any).getCellRaw?.(row, col);
      if (raw) {
        valueType = raw.t ?? null;
        hasRichText = !!raw.p;
        hasFormula = typeof raw.f === 'string' && raw.f.length > 0;
      }

      // 检测合并区域 — 使用 getMergedRanges() 遍历所有合并区域
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const wsAny = ws as any;
      let mergeRange: CellInfo['mergeRange'] = null;

      // 方式1: getMergedRanges() — 返回所有合并区域的 FRange[]
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const allMerges: any[] = wsAny.getMergedRanges?.() ?? [];
      for (const mr of allMerges) {
        const mrRow = mr.getRow() as number;
        const mrCol = mr.getColumn() as number;
        const mrHeight = mr.getHeight() as number;
        const mrWidth = mr.getWidth() as number;
        if (row >= mrRow && row < mrRow + mrHeight && col >= mrCol && col < mrCol + mrWidth) {
          mergeRange = {
            startRow: mrRow,
            startColumn: mrCol,
            endRow: mrRow + mrHeight - 1,
            endColumn: mrCol + mrWidth - 1,
          };
          break;
        }
      }

      // 方式2: getCellMergeData() — 直接查询指定单元格是否在合并区域内
      if (!mergeRange) {
        const mergeFRange = wsAny.getCellMergeData?.(row, col);
        if (mergeFRange) {
          const mStartRow = mergeFRange.getRow() as number;
          const mStartCol = mergeFRange.getColumn() as number;
          mergeRange = {
            startRow: mStartRow,
            startColumn: mStartCol,
            endRow: mStartRow + (mergeFRange.getHeight() as number) - 1,
            endColumn: mStartCol + (mergeFRange.getWidth() as number) - 1,
          };
        }
      }

      // 读取当前单元格样式（用于回填样式编辑器）
      const currentStyle = readCellStyle(univerAPI, row, col);

      setCellInfo({
        sheetId,
        position: a1,
        row,
        col,
        value: value as string | number | boolean | null,
        valueType,
        hasRichText,
        hasFormula,
        jsType: value === null || value === undefined ? 'null' : typeof value,
        mergeRange,
        currentStyle,
      });

      // selection 变更后重新应用高亮
      requestAnimationFrame(() => reapplyHighlights());
    });

    // 拖拽事件：支持从外部拖入字段到单元格
    const container = containerRef.current!;
    const handleDragOver = (e: DragEvent) => {
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
    };
    const handleDrop = (e: DragEvent) => {
      e.preventDefault();
      const displayText = e.dataTransfer?.getData('text/plain');
      const fieldCode = e.dataTransfer?.getData('application/x-field-code');
      if (!displayText) return;

      const wb = univerAPI.getActiveWorkbook();
      const ws = wb?.getActiveSheet();
      const range = ws?.getActiveRange();
      if (!ws || !range) return;

      const sheetId = ws.getSheetId?.() as string;
      const row = range.getRow() as number;
      const col = range.getColumn() as number;

      // 写入显示文本到单元格
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (range as any).setValue?.(displayText);

      // 如果有字段编码，自动添加 field 属性绑定
      if (fieldCode) {
        const ck = makeCellKey(sheetId, row, col);
        setPropStore((prev) => {
          const existing = prev.cellProps[ck] || [];
          const fieldProp: CellProp = {
            kind: 'field',
            field: fieldCode,
            data: { display: 'value' },
          };
          return {
            ...prev,
            cellProps: { ...prev.cellProps, [ck]: [...existing, fieldProp] },
          };
        });
        console.log(`✅ [拖入] "${displayText}" → (${row},${col}) 字段: ${fieldCode}`);
      } else {
        console.log(`✅ [拖入] "${displayText}" → (${row},${col})`);
      }
    };
    container.addEventListener('dragover', handleDragOver);
    container.addEventListener('drop', handleDrop);

    console.log('✅ [Univer 验证] 手动 plugins 模式初始化完成');

    return () => {
      container.removeEventListener('dragover', handleDragOver);
      container.removeEventListener('drop', handleDrop);
      highlightDisposablesRef.current.forEach((d) => {
        try { d.dispose(); } catch { /* ignore */ }
      });
      univerAPI.dispose();
    };
  }, [reapplyHighlights]);

  // ─── 当前选中的属性上下文 ──────────────────────────

  const currentContext = useMemo(() => {
    if (!cellInfo) return null;

    const { sheetId, row, col, mergeRange } = cellInfo;

    // 检查是否在循环块内
    const inLoopBlock = loopBlocks.find(
      (b) => b.sheetId === sheetId
        && row >= b.startRow && row <= b.endRow
        && col >= b.startColumn && col <= b.endColumn,
    );

    if (mergeRange) {
      const mk = makeMergeKey(sheetId, mergeRange.startRow, mergeRange.startColumn, mergeRange.endRow, mergeRange.endColumn);
      return {
        type: 'merge' as const,
        key: mk,
        label: `合并区域 (${mergeRange.startRow},${mergeRange.startColumn}) → (${mergeRange.endRow},${mergeRange.endColumn})`,
        props: propStore.mergeProps[mk] || [],
        loopBlock: inLoopBlock || null,
      };
    }

    if (inLoopBlock) {
      return {
        type: 'loopBlock' as const,
        key: inLoopBlock.id,
        label: `循环块: ${inLoopBlock.label}`,
        props: propStore.loopBlockProps[inLoopBlock.id] || [],
        loopBlock: inLoopBlock,
      };
    }

    const ck = makeCellKey(sheetId, row, col);
    return {
      type: 'cell' as const,
      key: ck,
      label: cellInfo.position,
      props: propStore.cellProps[ck] || [],
      loopBlock: null,
    };
  }, [cellInfo, propStore, loopBlocks]);

  // ─── 属性 CRUD ──────────────────────────────────

  const updateProps = useCallback((target: 'cell' | 'merge' | 'loopBlock', key: string, updater: (prev: CellProp[]) => CellProp[]) => {
    setPropStore((prev) => {
      const storeKey = target === 'cell' ? 'cellProps' : target === 'merge' ? 'mergeProps' : 'loopBlockProps';
      const current = prev[storeKey][key] || [];
      return { ...prev, [storeKey]: { ...prev[storeKey], [key]: updater(current) } };
    });
  }, []);

  const handleAddProp = useCallback(() => {
    if (!currentContext || !addingPropKind) return;

    const template = PROP_KIND_MAP[addingPropKind];
    if (!template) return;

    const data: Record<string, unknown> = { ...template.createDefault() };
    // 覆盖用户在表单中输入的值
    for (const [k, v] of Object.entries(newPropData)) {
      data[k] = v;
    }

    const newProp: CellProp = {
      kind: addingPropKind,
      data,
    };
    if (newPropField) newProp.field = newPropField;

    updateProps(currentContext.type, currentContext.key, (prev) => [...prev, newProp]);

    // 重置表单
    setAddingPropKind(null);
    setNewPropField('');
    setNewPropData({});
  }, [currentContext, addingPropKind, newPropField, newPropData, updateProps]);

  const handleRemoveProp = useCallback((index: number) => {
    if (!currentContext) return;
    updateProps(currentContext.type, currentContext.key, (prev) => prev.filter((_, i) => i !== index));
  }, [currentContext, updateProps]);

  // ─── 循环块 ──────────────────────────────────

  const handleCreateLoopBlock = () => {
    const api = univerAPIRef.current;
    if (!api) return;

    const workbook = api.getActiveWorkbook();
    if (!workbook) return;
    const sheet = workbook.getActiveSheet();
    if (!sheet) return;

    const activeRange = sheet.getActiveRange();
    if (!activeRange) return;

    const startRow = activeRange.getRow();
    const startColumn = activeRange.getColumn();
    const height = activeRange.getHeight();
    const width = activeRange.getWidth();
    const sheetId = sheet.getSheetId();

    const block: LoopBlock = {
      id: genBlockId(),
      sheetId,
      startRow,
      startColumn,
      endRow: startRow + height - 1,
      endColumn: startColumn + width - 1,
      label: `循环块 ${loopBlocks.length + 1}`,
      loopVariable: '',
    };

    setLoopBlocks((prev) => [...prev, block]);
    console.log(`✅ [循环块] 创建: ${block.label} (${startRow},${startColumn}) → (${block.endRow},${block.endColumn})`);
  };

  const handleClearLoopBlocks = () => {
    highlightDisposablesRef.current.forEach((d) => {
      try { d.dispose(); } catch { /* ignore */ }
    });
    highlightDisposablesRef.current = [];
    setLoopBlocks([]);
    setPropStore((prev) => ({ ...prev, loopBlockProps: {} }));
    console.log('✅ [循环块] 已清除所有循环块');
  };

  const handleLoopBlockVariableChange = useCallback((blockId: string, variable: string) => {
    setLoopBlocks((prev) => prev.map((b) => b.id === blockId ? { ...b, loopVariable: variable } : b));
  }, []);

  // ─── 快照 ──────────────────────────────────

  const handleSnapshot = () => {
    const fWorkbook = univerAPIRef.current?.getActiveWorkbook();
    if (!fWorkbook) {
      console.warn('⚠️ 未找到活动工作簿');
      return;
    }

    // 诊断: 打印合并数据和属性统计
    const sheet = fWorkbook.getActiveSheet();
    if (sheet) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const merges: any[] = (sheet as any).getMergedRanges?.() ?? [];
      console.log(`🔍 [诊断] 工作表共有 ${merges.length} 个合并区域`);
      merges.forEach((mr: { getA1Notation: () => string; getRow: () => number; getColumn: () => number; getHeight: () => number; getWidth: () => number }) => {
        console.log(`   - ${mr.getA1Notation()} (${mr.getRow()},${mr.getColumn()}) → (${mr.getRow() + mr.getHeight() - 1},${mr.getColumn() + mr.getWidth() - 1})`);
      });
    }

    const cellPropCount = Object.keys(propStore.cellProps).length;
    const mergePropCount = Object.keys(propStore.mergeProps).length;
    const loopPropCount = Object.keys(propStore.loopBlockProps).length;
    console.log(`🔍 [诊断] 属性绑定: 单元格 ${cellPropCount} 个, 合并区域 ${mergePropCount} 个, 循环块 ${loopPropCount} 个`);
    console.log(`🔍 [诊断] 循环块: ${loopBlocks.length} 个`);

    extractWorkbookSnapshot(fWorkbook, propStore, loopBlocks);
  };

  // ─── 渲染快照 ──────────────────────────────────

  const handleRender = () => {
    if (!univerAPIRef.current) return;
    renderWorkbookSnapshot(univerAPIRef.current, MOCK_SNAPSHOT, (result) => {
      // 合并属性到 propStore
      setPropStore((prev) => ({
        cellProps: { ...prev.cellProps, ...result.cellProps },
        mergeProps: { ...prev.mergeProps, ...result.mergeProps },
        loopBlockProps: { ...prev.loopBlockProps, ...result.loopBlockProps },
      }));
      // 添加循环块
      setLoopBlocks((prev) => [...prev, ...result.loopBlocks]);
    });
  };

  // ─── 渲染 ──────────────────────────────────

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* 工具栏 */}
      <div style={{
        padding: '8px 12px',
        borderBottom: '1px solid #e8e8e8',
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}>
        <Space>
          <Button type="primary" icon={<DatabaseOutlined />} onClick={handleSnapshot}>
            获取快照
          </Button>
          <Button icon={<PlayCircleOutlined />} onClick={handleRender}>
            渲染快照
          </Button>
          <Button icon={<HighlightOutlined />} onClick={handleCreateLoopBlock}>
            创建循环块
          </Button>
          {loopBlocks.length > 0 && (
            <Button icon={<DeleteOutlined />} danger onClick={handleClearLoopBlocks}>
              清除循环块 ({loopBlocks.length})
            </Button>
          )}
        </Space>

        {/* 可拖拽字段 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 8 }}>
          <span style={{ fontSize: 12, color: '#999' }}>拖入字段:</span>
          {mockDataConfig.tables.slice(0, 2).flatMap((table) =>
            table.fields.slice(0, 3).map((f) => {
              const fieldCode = `${table.name}.${f.name}`;
              const displayName = f.alias || f.name;
              return (
                <span
                  key={fieldCode}
                  draggable
                  onDragStart={(e) => {
                    e.dataTransfer.setData('text/plain', displayName);
                    e.dataTransfer.setData('application/x-field-code', fieldCode);
                    e.dataTransfer.effectAllowed = 'copy';
                  }}
                  style={{
                    display: 'inline-block',
                    padding: '2px 8px',
                    background: '#f0f5ff',
                    border: '1px solid #adc6ff',
                    borderRadius: 4,
                    fontSize: 12,
                    cursor: 'grab',
                    userSelect: 'none',
                  }}
                >
                  {displayName}
                </span>
              );
            })
          )}
        </div>

        {cellInfo && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <Tag color="default" style={{ margin: 0 }}>{cellInfo.position}</Tag>

            {cellInfo.valueType !== null && (
              <Tag color={VALUE_TYPE_MAP[cellInfo.valueType]?.color || 'default'} style={{ margin: 0 }}>
                {VALUE_TYPE_MAP[cellInfo.valueType]?.label || `type:${cellInfo.valueType}`}
              </Tag>
            )}

            <Tag color="purple" style={{ margin: 0 }}>JS: {cellInfo.jsType}</Tag>

            {cellInfo.hasRichText && <Tag color="cyan" style={{ margin: 0 }}>富文本</Tag>}
            {cellInfo.hasFormula && <Tag color="red" style={{ margin: 0 }}>公式</Tag>}
            {cellInfo.mergeRange && <Tag color="orange" style={{ margin: 0 }}>合并单元格</Tag>}

            <span style={{ color: '#666', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {cellInfo.value !== null ? String(cellInfo.value) : '(空)'}
            </span>
          </div>
        )}
      </div>

      {/* 主体：表格 + 属性面板 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Univer 电子表格容器 */}
        <div ref={containerRef} style={{ flex: 1, overflow: 'hidden' }} />

        {/* 右侧属性面板 */}
        <div style={{
          width: 300,
          borderLeft: '1px solid #e8e8e8',
          overflow: 'auto',
          padding: 12,
          flexShrink: 0,
        }}>
          {!cellInfo || !currentContext ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择单元格" />
          ) : (
            <>
              {/* 选中信息头 */}
              <div style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <Tag color={currentContext.type === 'merge' ? 'orange' : currentContext.type === 'loopBlock' ? 'green' : 'blue'}>
                    {currentContext.type === 'merge' ? '合并区域' : currentContext.type === 'loopBlock' ? '循环块' : '单元格'}
                  </Tag>
                  <span style={{ fontWeight: 500 }}>{currentContext.label}</span>
                </div>

                {/* 合并区域详细信息 */}
                {cellInfo.mergeRange && (
                  <div style={{
                    padding: '6px 10px',
                    background: '#fff7e6',
                    border: '1px solid #ffd591',
                    borderRadius: 6,
                    fontSize: 12,
                    marginTop: 4,
                  }}>
                    <div style={{ fontWeight: 500, marginBottom: 2 }}>合并范围</div>
                    <div style={{ color: '#666' }}>
                      起始: ({cellInfo.mergeRange.startRow}, {cellInfo.mergeRange.startColumn}) →
                      结束: ({cellInfo.mergeRange.endRow}, {cellInfo.mergeRange.endColumn})
                    </div>
                    <div style={{ color: '#666' }}>
                      跨 {cellInfo.mergeRange.endRow - cellInfo.mergeRange.startRow + 1} 行 × {cellInfo.mergeRange.endColumn - cellInfo.mergeRange.startColumn + 1} 列
                    </div>
                  </div>
                )}
              </div>

              {/* 循环块配置（如果选中在循环块内） */}
              {currentContext.loopBlock && currentContext.type !== 'loopBlock' && (
                <div style={{
                  padding: '8px 10px',
                  background: '#f6ffed',
                  border: '1px solid #b7eb8f',
                  borderRadius: 6,
                  marginBottom: 12,
                  fontSize: 12,
                }}>
                  <div style={{ fontWeight: 500, marginBottom: 4 }}>所在循环块: {currentContext.loopBlock.label}</div>
                  <div style={{ color: '#666' }}>
                    范围: ({currentContext.loopBlock.startRow},{currentContext.loopBlock.startColumn}) → ({currentContext.loopBlock.endRow},{currentContext.loopBlock.endColumn})
                  </div>
                  {currentContext.loopBlock.loopVariable && (
                    <div style={{ color: '#666' }}>循环变量: {currentContext.loopBlock.loopVariable}</div>
                  )}
                </div>
              )}

              {/* 循环块变量配置（当选中类型为循环块时） */}
              {currentContext.type === 'loopBlock' && currentContext.loopBlock && (
                <div style={{ marginBottom: 12 }}>
                  <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>循环变量</div>
                  <Select
                    size="small"
                    style={{ width: '100%' }}
                    placeholder="选择循环变量字段"
                    allowClear
                    value={currentContext.loopBlock.loopVariable || undefined}
                    options={fieldOptions}
                    onChange={(val) => handleLoopBlockVariableChange(currentContext.loopBlock!.id, val || '')}
                  />
                </div>
              )}

              {/* 样式设置 — key 绑定单元格位置，切换时自动重建并回填当前样式 */}
              <Divider style={{ margin: '8px 0' }}>样式设置</Divider>
              <div key={cellInfo.position} style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 4 }}>
                {(() => {
                  const cs = cellInfo.currentStyle;
                  return (
                    <>
                      <div style={{ display: 'flex', gap: 8 }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>字体颜色</div>
                          <input
                            type="color"
                            defaultValue={cs.fontColor || '#000000'}
                            onChange={(e) => applyCellStyle(univerAPIRef.current, cellInfo.row, cellInfo.col, { fontColor: e.target.value })}
                            style={{ width: '100%', height: 28, border: '1px solid #d9d9d9', borderRadius: 4, cursor: 'pointer', padding: 2 }}
                          />
                        </div>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>背景颜色</div>
                          <input
                            type="color"
                            defaultValue={cs.background || '#ffffff'}
                            onChange={(e) => applyCellStyle(univerAPIRef.current, cellInfo.row, cellInfo.col, { background: e.target.value })}
                            style={{ width: '100%', height: 28, border: '1px solid #d9d9d9', borderRadius: 4, cursor: 'pointer', padding: 2 }}
                          />
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: 8 }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>字号</div>
                          <Input
                            size="small"
                            type="number"
                            min={8}
                            max={72}
                            defaultValue={cs.fontSize}
                            placeholder="13"
                            onChange={(e) => {
                              const size = parseInt(e.target.value);
                              if (size >= 8) applyCellStyle(univerAPIRef.current, cellInfo.row, cellInfo.col, { fontSize: size });
                            }}
                          />
                        </div>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>加粗 {cs.bold ? '(当前加粗)' : ''}</div>
                          <Space size={4}>
                            <Button size="small" type={cs.bold ? 'primary' : 'default'} onClick={() => applyCellStyle(univerAPIRef.current, cellInfo.row, cellInfo.col, { fontWeight: 'bold' })}>
                              <strong>B</strong>
                            </Button>
                            <Button size="small" type={!cs.bold ? 'primary' : 'default'} onClick={() => applyCellStyle(univerAPIRef.current, cellInfo.row, cellInfo.col, { fontWeight: 'normal' })}>
                              取消
                            </Button>
                          </Space>
                        </div>
                      </div>
                    </>
                  );
                })()}
                <Button size="small" danger onClick={() => clearCellStyle(univerAPIRef.current, cellInfo.row, cellInfo.col)}>
                  清除样式
                </Button>
              </div>

              <Divider style={{ margin: '8px 0' }}>属性列表 ({currentContext.props.length})</Divider>

              {/* 已有属性列表 */}
              {currentContext.props.length === 0 ? (
                <div style={{ fontSize: 12, color: '#999', textAlign: 'center', padding: '12px 0' }}>
                  暂无属性，点击下方添加
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}>
                  {currentContext.props.map((prop, index) => (
                    <PropCard key={index} prop={prop} onRemove={() => handleRemoveProp(index)} />
                  ))}
                </div>
              )}

              <Divider style={{ margin: '8px 0' }}>添加属性</Divider>

              {/* 添加属性表单 */}
              {addingPropKind ? (
                <PropAddForm
                  kind={addingPropKind}
                  field={newPropField}
                  data={newPropData}
                  onFieldChange={setNewPropField}
                  onDataChange={setNewPropData}
                  onConfirm={handleAddProp}
                  onCancel={() => { setAddingPropKind(null); setNewPropData({}); setNewPropField(''); }}
                />
              ) : (
                <Select
                  size="small"
                  style={{ width: '100%' }}
                  placeholder="选择属性类型"
                  options={PROP_KINDS.map((k) => ({ label: k.label, value: k.kind }))}
                  onChange={(val) => {
                    setAddingPropKind(val);
                    setNewPropData({});
                    setNewPropField('');
                  }}
                />
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

// ─── 属性卡片 ────────────────────────────────────────

const PropCard: React.FC<{ prop: CellProp; onRemove: () => void }> = ({ prop, onRemove }) => {
  const template = PROP_KIND_MAP[prop.kind];
  return (
    <div style={{
      padding: '8px 10px',
      background: '#fafafa',
      border: '1px solid #e8e8e8',
      borderRadius: 6,
      fontSize: 12,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
        <Tag color="geekblue" style={{ margin: 0 }}>{template?.label || prop.kind}</Tag>
        <Button type="text" size="small" danger onClick={onRemove}>删除</Button>
      </div>
      {prop.field && (
        <div style={{ color: '#1677ff', marginBottom: 2 }}>字段: {prop.field}</div>
      )}
      <pre style={{
        margin: 0,
        padding: '4px 6px',
        background: '#fff',
        border: '1px solid #f0f0f0',
        borderRadius: 4,
        fontSize: 11,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
        maxHeight: 80,
        overflow: 'auto',
      }}>
        {JSON.stringify(prop.data, null, 2)}
      </pre>
    </div>
  );
};

// ─── 添加属性表单 ────────────────────────────────────

const PROP_FORM_FIELDS: Record<string, Array<{ key: string; label: string; placeholder: string }>> = {
  field: [
    { key: 'display', label: '显示方式', placeholder: 'value / label / both' },
  ],
  dataConfig: [
    { key: 'pageSize', label: '每页条数', placeholder: '10' },
    { key: 'orderBy', label: '排序字段', placeholder: 'fieldName' },
    { key: 'direction', label: '排序方向', placeholder: 'asc / desc' },
  ],
  display: [
    { key: 'format', label: '格式', placeholder: 'text / number / date / currency' },
    { key: 'prefix', label: '前缀', placeholder: '' },
    { key: 'suffix', label: '后缀', placeholder: '' },
  ],
  aggregation: [
    { key: 'method', label: '聚合方式', placeholder: 'count / sum / avg / max / min' },
    { key: 'groupBy', label: '分组字段', placeholder: '' },
  ],
};

const PropAddForm: React.FC<{
  kind: string;
  field: string;
  data: Record<string, string>;
  onFieldChange: (v: string) => void;
  onDataChange: (d: Record<string, string>) => void;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ kind, field, data, onFieldChange, onDataChange, onConfirm, onCancel }) => {
  const template: PropKindTemplate | undefined = PROP_KIND_MAP[kind];
  const formFields = PROP_FORM_FIELDS[kind] || [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <Tag color="geekblue">{template?.label || kind}</Tag>

      {/* 字段绑定（所有类型通用，可选） */}
      <div>
        <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>字段（可选）</div>
        <Select
          size="small"
          style={{ width: '100%' }}
          placeholder="选择数据源字段"
          allowClear
          value={field || undefined}
          options={fieldOptions}
          onChange={(val) => onFieldChange(val || '')}
        />
      </div>

      {/* 类型特定字段 */}
      {formFields.map((f) => (
        <div key={f.key}>
          <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>{f.label}</div>
          <Input
            size="small"
            placeholder={f.placeholder}
            value={data[f.key] || ''}
            onChange={(e) => onDataChange({ ...data, [f.key]: e.target.value })}
          />
        </div>
      ))}

      <Space size="small">
        <Button size="small" type="primary" icon={<PlusOutlined />} onClick={onConfirm}>
          确认添加
        </Button>
        <Button size="small" onClick={onCancel}>取消</Button>
      </Space>
    </div>
  );
};

export default UniverTestPage;
