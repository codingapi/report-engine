/**
 * 循环块边框管理
 * 在 Univer 工作表上绘制/清除蓝色虚线边框
 */

import type { LoopBlockConfig } from '../types';
import type { UniverAPI } from './setup';

/**
 * 同步循环块边框
 * 清除旧边框，绘制新边框
 */
export function syncLoopBlockBorders(
    univerAPI: UniverAPI,
    prevBlocks: Record<string, LoopBlockConfig>,
    nextBlocks: Record<string, LoopBlockConfig>,
): void {
    const workbook = univerAPI.getActiveWorkbook();
    if (!workbook) return;

    // 清除旧边框
    for (const block of Object.values(prevBlocks)) {
        try {
            const sheet = workbook.getSheetBySheetId(block.sheetId);
            if (!sheet) continue;
            const numRows = block.endRow - block.startRow + 1;
            const numCols = block.endColumn - block.startColumn + 1;
            const range = sheet.getRange(block.startRow, block.startColumn, numRows, numCols);
            range.setBorder(univerAPI.Enum.BorderType.OUTSIDE, univerAPI.Enum.BorderStyleTypes.NONE);
        } catch {
            // 工作表可能已被删除，忽略
        }
    }

    // 绘制新边框
    for (const block of Object.values(nextBlocks)) {
        try {
            const sheet = workbook.getSheetBySheetId(block.sheetId);
            if (!sheet) continue;
            const numRows = block.endRow - block.startRow + 1;
            const numCols = block.endColumn - block.startColumn + 1;
            const range = sheet.getRange(block.startRow, block.startColumn, numRows, numCols);
            range.setBorder(univerAPI.Enum.BorderType.OUTSIDE, univerAPI.Enum.BorderStyleTypes.DASHED, '#1677ff');
        } catch {
            // 渲染单元尚未就绪，忽略
        }
    }
}
