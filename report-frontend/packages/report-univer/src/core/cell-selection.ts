/**
 * 单元格选中事件处理
 * 注册 SelectionChanged 事件，返回 SelectedCellInfo + CellHandle + cellProps
 */

import type { SelectedCellInfo, CellHandle } from '@/types';
import type { UniverAPI } from './setup';
import type { HighlightManager } from './highlight';
import { createCellHandle } from './cell-handle';

/**
 * 注册单元格选中事件
 * @param univerAPI Univer Facade API
 * @param getCallback 获取最新回调的 getter
 * @param getHighlightManager 获取高亮管理器的 getter
 * @param getCellProps 获取当前单元格属性存储的 getter
 */
export function registerCellSelection<TCellProp = unknown>(
    univerAPI: UniverAPI,
    getCallback: () => ((info: SelectedCellInfo, handle: CellHandle, cellProps: TCellProp[] | undefined) => void) | undefined,
    getHighlightManager: () => HighlightManager | null,
    getCellProps: () => Record<string, TCellProp[]> | undefined,
): void {
    univerAPI.addEvent(univerAPI.Event.SelectionChanged, (params: Record<string, unknown>) => {
        const ws = params.worksheet as Record<string, Function>;
        const selections = params.selections as Array<{ startRow: number; startColumn: number }>;
        if (!selections || selections.length === 0) return;

        const { startRow: row, startColumn: col } = selections[0];
        const sheetId = ws.getSheetId?.() as string;

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
        if (callback) {
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

            // 创建操作句柄
            const handle = createCellHandle(univerAPI, sheetId, row, col, a1Notation);

            // 查找当前单元格的属性
            const cellPropsStore = getCellProps();
            const cellKey = `${sheetId}:${row}:${col}`;
            const cellProps = cellPropsStore?.[cellKey];

            callback(info, handle, cellProps);
        }

        // selection 变更后重新应用高亮（编辑单元格后 highlight 会被清除）
        const hm = getHighlightManager();
        if (hm && hm.hasRegions()) {
            requestAnimationFrame(() => hm.reapply());
        }
    });
}
