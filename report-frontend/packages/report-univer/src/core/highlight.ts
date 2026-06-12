/**
 * 循环块高亮管理
 * 使用 Univer highlightRanges API，支持 selection 变更后自动重应用
 */

import type { LoopBlockConfig } from '../types';
import type { UniverAPI } from './setup';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type IDisposable = any;

/** 高亮样式 */
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

interface HighlightRegion {
    id: string;
    sheetId: string;
    row: number;
    col: number;
    height: number;
    width: number;
}

export interface HighlightManager {
    /** 用最新 loopBlocks 同步高亮区域 */
    sync(blocks: Record<string, LoopBlockConfig>): void;
    /** 重新应用所有高亮（selection 变更后调用） */
    reapply(): void;
    /** 是否有高亮区域 */
    hasRegions(): boolean;
    /** 释放所有资源 */
    dispose(): void;
}

/**
 * 创建高亮管理器
 */
export function createHighlightManager(univerAPI: UniverAPI): HighlightManager {
    const regions: Map<string, HighlightRegion> = new Map();
    let disposables: IDisposable[] = [];

    function doApply(): void {
        // 释放旧的
        disposables.forEach(d => { try { d.dispose(); } catch { /* ignore */ } });
        disposables = [];

        if (regions.size === 0) return;

        const workbook = univerAPI.getActiveWorkbook();
        if (!workbook) return;

        // 按 sheetId 分组
        const sheetGroups = new Map<string, HighlightRegion[]>();
        regions.forEach(region => {
            const group = sheetGroups.get(region.sheetId) || [];
            group.push(region);
            sheetGroups.set(region.sheetId, group);
        });

        // 对每个 sheet 批量高亮
        sheetGroups.forEach((group, sheetId) => {
            try {
                const sheet = workbook.getSheetBySheetId(sheetId);
                if (!sheet) return;
                const ranges = group.map(r =>
                    sheet.getRange(r.row, r.col, r.height, r.width)
                );
                const disposable = sheet.highlightRanges(ranges, HIGHLIGHT_STYLE);
                disposables.push(disposable);
            } catch {
                // sheet 可能已被删除
            }
        });
    }

    function sync(blocks: Record<string, LoopBlockConfig>): void {
        // 移除不再存在的
        const toRemove: string[] = [];
        regions.forEach((_, id) => {
            if (!blocks[id]) toRemove.push(id);
        });
        toRemove.forEach(id => regions.delete(id));

        // 添加/更新存在的
        for (const [id, block] of Object.entries(blocks)) {
            regions.set(id, {
                id,
                sheetId: block.sheetId,
                row: block.startRow,
                col: block.startColumn,
                height: block.endRow - block.startRow + 1,
                width: block.endColumn - block.startColumn + 1,
            });
        }

        doApply();
    }

    return {
        sync,
        reapply: doApply,
        hasRegions: () => regions.size > 0,
        dispose: () => {
            disposables.forEach(d => { try { d.dispose(); } catch { /* ignore */ } });
            disposables = [];
            regions.clear();
        },
    };
}
