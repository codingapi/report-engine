/**
 * 循环块高亮管理
 * 使用 Univer highlightRanges API 绘制半透明覆盖，不修改单元格样式
 */

import type { LoopBlockConfig } from '../types';
import type { UniverAPI } from './setup';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type IDisposable = any;

/** 循环块高亮样式 */
const HIGHLIGHT_STYLE = {
    fill: 'rgba(22, 119, 255, 0.08)',
    stroke: '#1677ff',
    strokeWidth: 1,
    strokeDash: 8,
    widgets: { top: false, bottom: false, left: false, right: false, topLeft: false, topRight: false, bottomLeft: false, bottomRight: false },
};

/**
 * 同步循环块高亮
 * 通过 diff 新旧 blocks 管理 IDisposable 生命周期
 */
export function syncHighlights(
    univerAPI: UniverAPI,
    disposables: Map<string, IDisposable>,
    nextBlocks: Record<string, LoopBlockConfig>,
): Map<string, IDisposable> {
    const workbook = univerAPI.getActiveWorkbook();
    if (!workbook) return disposables;

    const nextDisposables = new Map<string, IDisposable>();

    // 保留仍然存在的 blocks 的 disposable
    disposables.forEach((disposable, id) => {
        if (nextBlocks[id]) {
            nextDisposables.set(id, disposable);
        } else {
            // 已删除的 block，释放高亮
            try { disposable.dispose(); } catch { /* ignore */ }
        }
    });

    // 新增 blocks 创建高亮
    for (const [id, block] of Object.entries(nextBlocks)) {
        if (nextDisposables.has(id)) continue;

        try {
            const sheet = workbook.getSheetBySheetId(block.sheetId);
            if (!sheet) continue;
            const numRows = block.endRow - block.startRow + 1;
            const numCols = block.endColumn - block.startColumn + 1;
            const range = sheet.getRange(block.startRow, block.startColumn, numRows, numCols);
            const disposable = sheet.highlightRanges([range], HIGHLIGHT_STYLE);
            nextDisposables.set(id, disposable);
        } catch {
            // 渲染单元尚未就绪，忽略
        }
    }

    return nextDisposables;
}

/**
 * 释放所有高亮 disposable
 */
export function disposeAllHighlights(disposables: Map<string, IDisposable>): void {
    disposables.forEach((disposable) => {
        try { disposable.dispose(); } catch { /* ignore */ }
    });
    disposables.clear();
}
