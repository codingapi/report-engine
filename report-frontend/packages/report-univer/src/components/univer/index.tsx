import { useEffect, useImperativeHandle, useRef, forwardRef } from 'react';
import { setupUniver } from '@/core/setup';
import type { UniverAPI } from '@/core/setup';
import { registerCellSelection } from '@/core/cell-selection';
import { buildContextMenus, updateMenuGroups } from '@/core/context-menu';
import { createHighlightManager } from '@/core/highlight';
import type { HighlightManager } from '@/core/highlight';
import { registerDragDrop } from '@/core/drag-drop';
import { extractSnapshot } from '@/core/snapshot';
import { renderSnapshot } from '@/core/render';
import type { UniverSheetProps, UniverSheetHandle, FontItem } from './type';

/** localStorage 缓存 key */
const FONT_CACHE_KEY = 'report-fonts-v2';

export const UniverSheet = forwardRef<UniverSheetHandle, UniverSheetProps>((props, ref) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const univerAPIRef = useRef<UniverAPI>(null);
  const highlightManagerRef = useRef<HighlightManager | null>(null);
  const menusBuiltRef = useRef(false);

  // 用 ref 保存最新回调，避免闭包过期
  const onCellSelectRef = useRef(props.onCellSelect);
  onCellSelectRef.current = props.onCellSelect;

  const onFieldDropRef = useRef(props.onFieldDrop);
  onFieldDropRef.current = props.onFieldDrop;

  const onCellValueChangeRef = useRef(props.onCellValueChange);
  onCellValueChangeRef.current = props.onCellValueChange;

  const onSelectionClearRef = useRef(props.onSelectionClear);
  onSelectionClearRef.current = props.onSelectionClear;

  // 用 ref 保存最新的属性存储（供 cell-selection 回调查找）
  const cellPropsRef = useRef(props.cellProps);
  cellPropsRef.current = props.cellProps;

  // 保存当前组件 props 引用（供 getSnapshot 使用）
  const propsRef = useRef(props);
  propsRef.current = props;

  const style = props.style || { height: '100vh' };

  // 暴露命令式句柄
  useImperativeHandle(ref, () => ({
    getSnapshot: () => {
      const api = univerAPIRef.current;
      if (!api) return null;
      const workbook = api.getActiveWorkbook();
      if (!workbook) return null;
      const p = propsRef.current;
      return extractSnapshot(workbook, {
        cellProps: p.cellProps,
        mergeProps: p.mergeProps,
        loopBlocks: p.loopBlocks ? Object.values(p.loopBlocks) : undefined,
        loopBlockProps: p.loopBlockProps,
      });
    },

    loadSnapshot: (snapshot) => {
      const api = univerAPIRef.current;
      if (!api) return null;
      return renderSnapshot(api, snapshot);
    },

    setCellValue: (sheetId: string, row: number, column: number, value: string) => {
      const api = univerAPIRef.current;
      if (!api) return;
      const workbook = api.getActiveWorkbook();
      if (!workbook) return;
      const sheet = workbook.getSheetBySheetId(sheetId);
      if (!sheet) return;
      sheet.getRange(row, column).setValue(value);
    },

    setSheetName: (sheetId: string, name: string) => {
      const api = univerAPIRef.current;
      if (!api) return;
      const workbook = api.getActiveWorkbook();
      if (!workbook) return;
      const sheet = workbook.getSheetBySheetId(sheetId);
      if (sheet) sheet.setName(name);
    },

    setSheetSize: (sheetId: string, rowCount: number, columnCount: number) => {
      const api = univerAPIRef.current;
      if (!api) return;
      const workbook = api.getActiveWorkbook();
      if (!workbook) return;
      const sheet = workbook.getSheetBySheetId(sheetId);
      if (!sheet) return;
      sheet.setRowCount(rowCount);
      sheet.setColumnCount(columnCount);
    },

    addFonts: (fonts) => {
      const api = univerAPIRef.current;
      if (!api || fonts.length === 0) return;
      api.addFonts(fonts);
    },
  }));

  // 初始化 Univer（仅一次）
  useEffect(() => {
    if (!containerRef.current) return;

    const { univerAPI, dispose } = setupUniver(containerRef.current);
    univerAPIRef.current = univerAPI;

    // 创建高亮管理器
    highlightManagerRef.current = createHighlightManager(univerAPI);

    // 注册单元格选中事件（含 CellHandle + cellProps + DOM click 保底）
    registerCellSelection(
      univerAPI,
      containerRef.current,
      () => onCellSelectRef.current,
      () => highlightManagerRef.current,
      () => cellPropsRef.current,
    );

    // 构建右键菜单
    if (props.contextMenuGroups?.length) {
      buildContextMenus(univerAPI, props.contextMenuGroups);
      menusBuiltRef.current = true;
    }

    // 注册拖拽事件（含 CellHandle）
    const cleanupDragDrop = registerDragDrop(
      containerRef.current,
      univerAPI,
      () => onFieldDropRef.current,
    );

    // 监听单元格值变更（用户编辑后同步到外部状态）
    const cellChangeDisposable = univerAPI.addEvent(
      univerAPI.Event.CommandExecuted,
      (event: {
        id: string;
        params?: {
          subUnitId?: string;
          cellValue?: Record<string, Record<string, { v?: unknown }>>;
        };
      }) => {
        if (event.id !== 'sheet.mutation.set-range-values') return;
        if (!onCellValueChangeRef.current) return;
        const p = event.params;
        if (!p?.cellValue) return;
        const sheetId = p.subUnitId || '';
        const changes: Array<{ sheetId: string; row: number; col: number; value: string }> = [];
        for (const [rowStr, cols] of Object.entries(p.cellValue)) {
          const row = Number(rowStr);
          if (Number.isNaN(row)) continue;
          for (const [colStr, cell] of Object.entries(cols as Record<string, { v?: unknown }>)) {
            const col = Number(colStr);
            if (Number.isNaN(col)) continue;
            // cell 为 null：ClearSelectionAllCommand（全部清除）用 generateNullCell 生成，
            // 整个 cell 置 null，视为内容清除。
            if (cell == null) {
              changes.push({ sheetId, row, col, value: '' });
              continue;
            }
            // 仅当 mutation 真正携带值字段 v 时才视为内容变更。
            // 边框/填充等纯样式操作只带 s（样式）不带 v，若误读为空字符串，
            // 会把汇总行等已设内容同步清空（本格内容消失）。
            // 真正的删除内容操作 Univer 会显式带 v: null，'v' in cell 仍为真，不受影响。
            // ClearSelectionFormatCommand（清除格式）用 generateNullCellStyle → { s: null }，无 v → 跳过。
            if (!('v' in cell)) continue;
            const raw = cell.v;
            const value = raw == null ? '' : String(raw);
            changes.push({ sheetId, row, col, value });
          }
        }
        if (changes.length > 0) onCellValueChangeRef.current(changes);
      },
    );

    // 监听选区清除命令（清除内容 / 清除格式 / 全部清除）
    const CLEAR_COMMANDS = new Set([
      'sheet.command.clear-selection-content',
      'sheet.command.clear-selection-format',
      'sheet.command.clear-selection-all',
    ]);
    const workbook = univerAPI.getActiveWorkbook();
    const clearDisposable = workbook?.onCommandExecuted?.((cmd: { id: string }) => {
      if (!CLEAR_COMMANDS.has(cmd.id)) return;
      const cb = onSelectionClearRef.current;
      if (!cb) return;
      const wb = univerAPI.getActiveWorkbook();
      if (!wb) return;
      const sheet = wb.getActiveSheet();
      if (!sheet) return;
      const sheetId = sheet.getSheetId?.() as string;
      const range = wb.getActiveRange();
      if (!range) return;
      const startRow = range.getRow() as number;
      const startCol = range.getColumn() as number;
      const rowCount = range.getRowCount() as number;
      const colCount = range.getColumnCount() as number;
      const cellKeys: string[] = [];
      for (let r = startRow; r < startRow + rowCount; r++) {
        for (let c = startCol; c < startCol + colCount; c++) {
          cellKeys.push(`${sheetId}:${r}:${c}`);
        }
      }
      if (cellKeys.length > 0) cb(cellKeys);
    });

    // 延迟通知父组件，确保 useImperativeHandle 已执行、ref 可用
    setTimeout(() => props.onReady?.(), 0);

    return () => {
      cleanupDragDrop();
      cellChangeDisposable?.dispose();
      clearDisposable?.dispose?.();
      highlightManagerRef.current?.dispose();
      highlightManagerRef.current = null;
      dispose();
    };
  }, []);

  // 字体加载（Univer 初始化后自动执行）
  useEffect(() => {
    if (!props.onFontRequest) return;

    const loadFonts = async () => {
      try {
        // 优先从 localStorage 读取缓存
        let items: FontItem[] | null = null;
        try {
          const cached = localStorage.getItem(FONT_CACHE_KEY);
          if (cached) items = JSON.parse(cached);
        } catch {
          /* 缓存解析失败，走回调 */
        }

        // 缓存未命中，请求父组件提供字体数据
        if (!items) {
          const result = await props.onFontRequest!();
          if (result && result.length > 0) {
            items = result;
            try {
              localStorage.setItem(FONT_CACHE_KEY, JSON.stringify(items));
            } catch {
              /* 忽略 */
            }
          }
        }

        if (!items || items.length === 0) return;

        // .ttc（TrueType Collection）浏览器 @font-face 无法加载，跳过
        const loadable = items.filter((item) => !item.filename.toLowerCase().endsWith('.ttc'));
        if (loadable.length === 0) return;

        // 注入 @font-face 规则
        const styleId = 'report-engine-fonts';
        if (!document.getElementById(styleId)) {
          const styleEl = document.createElement('style');
          styleEl.id = styleId;
          styleEl.textContent = loadable
            .map(
              (item) => `
@font-face {
  font-family: '${item.family}';
  src: url('${item.url}');
  font-display: swap;
}`,
            )
            .join('\n');
          document.head.appendChild(styleEl);
        }

        // 注册到 Univer 字体下拉菜单
        const api = univerAPIRef.current;
        if (api) {
          api.addFonts(loadable.map((item) => ({ value: item.family, label: item.family })));
        }
      } catch {
        console.warn('字体加载失败，将仅使用内置字体');
      }
    };

    // 延迟执行，确保 useImperativeHandle 已完成
    const timer = setTimeout(loadFonts, 0);
    return () => clearTimeout(timer);
  }, [props.onFontRequest]);

  // 同步菜单定义
  useEffect(() => {
    if (props.contextMenuGroups) {
      updateMenuGroups(props.contextMenuGroups);

      if (!menusBuiltRef.current && props.contextMenuGroups.length && univerAPIRef.current) {
        buildContextMenus(univerAPIRef.current, props.contextMenuGroups);
        menusBuiltRef.current = true;
      }
    }
  }, [props.contextMenuGroups]);

  // 同步循环块高亮
  useEffect(() => {
    if (!highlightManagerRef.current) return;
    highlightManagerRef.current.sync(props.loopBlocks || {});
  }, [props.loopBlocks]);

  // 同步配置单元格高亮
  useEffect(() => {
    if (!highlightManagerRef.current) return;
    highlightManagerRef.current.syncCells(props.highlightCells || []);
  }, [props.highlightCells]);

  // 同步只读模式
  useEffect(() => {
    const api = univerAPIRef.current;
    if (!api) return;
    const workbook = api.getActiveWorkbook();
    if (!workbook) return;
    workbook.setEditable(!props.readOnly);
  }, [props.readOnly]);

  // 显示消息提示
  useEffect(() => {
    if (!props.message || !univerAPIRef.current) return;

    univerAPIRef.current.showMessage({
      content: props.message.content,
      type: props.message.type || 'info',
      duration: props.message.duration ?? 2000,
    });
    props.onMessageConsumed?.();
  }, [props.message]);

  return <div ref={containerRef} style={style} />;
});

UniverSheet.displayName = 'UniverSheet';
