/**
 * 单元格选中事件处理
 * 注册 SelectionChanged 事件 + DOM click 保底监听，返回 SelectedCellInfo + CellHandle + cellProps
 *
 * 为什么需要 click 保底：loadSnapshot 后 Univer 不会重置选中状态，
 * 如果用户点击的位置恰好与旧选中相同，SelectionChanged 不触发。
 * DOM click 监听确保每次用户点击都能读取当前选中并触发回调。
 */

import type { SelectedCellInfo, CellHandle } from '@/types';
import type { UniverAPI } from './setup';
import type { HighlightManager } from './highlight';
import { createCellHandle } from './cell-handle';

/**
 * 注册单元格选中事件
 * @param univerAPI Univer Facade API
 * @param container Univer 容器 DOM 元素（用于 click 保底监听）
 * @param getCallback 获取最新回调的 getter
 * @param getHighlightManager 获取高亮管理器的 getter
 * @param getCellProps 获取当前单元格属性存储的 getter
 */
export function registerCellSelection<TCellProp = unknown>(
  univerAPI: UniverAPI,
  container: HTMLElement,
  getCallback: () =>
    | ((info: SelectedCellInfo, handle: CellHandle, cellProps: TCellProp[] | undefined) => void)
    | undefined,
  getHighlightManager: () => HighlightManager | null,
  getCellProps: () => Record<string, TCellProp[]> | undefined,
): void {
  // ─── 共享：从 worksheet + row/col 构建信息并触发回调 ───
  function fireSelection(ws: Record<string, Function>, row: number, col: number) {
    const sheetId = ws.getSheetId?.() as string;
    if (!sheetId) return;

    // 剥离公式前缀
    const rawCell = ws.getCellRaw?.(row, col) as Record<string, unknown> | undefined;
    if (rawCell && typeof rawCell.f === 'string' && (rawCell.f as string).startsWith('=')) {
      const plainText = (rawCell.f as string).slice(1);
      const fRange = (ws as Record<string, Function>).getRange?.(row, col);
      if (fRange) {
        (fRange as Record<string, Function>).setValue?.(plainText);
      }
    }

    const callback = getCallback();
    if (!callback) return;

    const fRange = (ws as Record<string, Function>).getRange?.(row, col);
    const a1Notation = fRange?.getA1Notation?.() ?? '';
    const value = fRange?.getValue?.() ?? null;

    // 合并单元格信息
    const mergeRange = ws.getCellMergeData?.(row, col) as Record<string, Function> | undefined;
    let mergeInfo: SelectedCellInfo['mergeRange'] = null;

    if (mergeRange) {
      const mergeStartRow = mergeRange.getRow() as number;
      const mergeStartCol = mergeRange.getColumn() as number;
      mergeInfo = {
        startRow: mergeStartRow,
        startColumn: mergeStartCol,
        endRow: mergeStartRow + (mergeRange.getHeight() as number) - 1,
        endColumn: mergeStartCol + (mergeRange.getWidth() as number) - 1,
        a1Notation: mergeRange.getA1Notation() as string,
      };
    }

    const info: SelectedCellInfo = {
      sheetId,
      row,
      column: col,
      a1Notation,
      value,
      mergeRange: mergeInfo,
    };

    const handle = createCellHandle(univerAPI, sheetId, row, col, a1Notation);

    const cellPropsStore = getCellProps();
    const cellKey = `${sheetId}:${row}:${col}`;
    const cellProps = cellPropsStore?.[cellKey];

    callback(info, handle, cellProps);

    // selection 变更后重新应用高亮
    const hm = getHighlightManager();
    if (hm && hm.hasRegions()) {
      requestAnimationFrame(() => hm.reapply());
    }
  }

  // ─── 主通道：Univer SelectionChanged 事件 ───
  univerAPI.addEvent(univerAPI.Event.SelectionChanged, (params: Record<string, unknown>) => {
    const ws = params.worksheet as Record<string, Function>;
    const selections = params.selections as Array<{ startRow: number; startColumn: number }>;
    if (!selections || selections.length === 0) return;

    const { startRow: row, startColumn: col } = selections[0];
    fireSelection(ws, row, col);
  });

  // ─── 保底通道：DOM click 监听 ───
  // 用户点击容器后，延迟一帧读取 Univer 当前选中状态并触发回调。
  // 解决 loadSnapshot 后首次点击同一位置不触发 SelectionChanged 的问题。
  let lastFiredKey = '';

  container.addEventListener('click', () => {
    requestAnimationFrame(() => {
      try {
        const workbook = univerAPI.getActiveWorkbook?.();
        const ws = workbook?.getActiveSheet?.();
        if (!ws) return;

        const range = ws.getActiveRange?.();
        if (!range) return;

        const row = range.getRow?.() as number;
        const col = range.getColumn?.() as number;
        if (row == null || col == null) return;

        const sheetId = ws.getSheetId?.() as string;
        const key = `${sheetId}:${row}:${col}`;

        // 去重：如果 SelectionChanged 已经触发了同一格，不再重复
        if (key === lastFiredKey) return;
        lastFiredKey = key;

        fireSelection(ws as Record<string, Function>, row, col);
      } catch {
        // 静默忽略保底通道的异常
      }
    });
  });

  // SelectionChanged 触发时记录最后一次 key，供 click 去重
  univerAPI.addEvent(univerAPI.Event.SelectionChanged, (params: Record<string, unknown>) => {
    const ws = params.worksheet as Record<string, Function>;
    const selections = params.selections as Array<{ startRow: number; startColumn: number }>;
    if (!selections || selections.length === 0) return;
    const { startRow: row, startColumn: col } = selections[0];
    const sheetId = ws.getSheetId?.() as string;
    lastFiredKey = `${sheetId}:${row}:${col}`;
  });
}
