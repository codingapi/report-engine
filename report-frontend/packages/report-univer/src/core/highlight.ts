/**
 * 高亮管理
 * 使用 Univer highlightRanges API（非持久装饰层，不写入 snapshot/导出），
 * 支持两类高亮：循环块区域 + 配置单元格。selection 变更后自动重应用。
 */

import type { LoopBlockConfig, CellRange } from '@/types';
import type { UniverAPI } from './setup';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type IDisposable = any;

/** 循环块区域高亮：淡蓝填充 + 蓝色虚线边框 */
const LOOP_HIGHLIGHT_STYLE = {
    fill: 'rgba(22, 119, 255, 0.08)',
    stroke: '#1677ff',
    strokeWidth: 1,
    strokeDash: 8,
    widgets: {
        top: false, bottom: false, left: false, right: false,
        topLeft: false, topRight: false, bottomLeft: false, bottomRight: false,
    },
};

/** 配置单元格高亮：较明显的蓝底 + 实线细边框，区分"有配置 vs 纯文字" */
const CELL_HIGHLIGHT_STYLE = {
    fill: 'rgba(22, 119, 255, 0.16)',
    stroke: 'rgba(22, 119, 255, 0.45)',
    strokeWidth: 1,
    strokeDash: 0,
    widgets: {
        top: false, bottom: false, left: false, right: false,
        topLeft: false, topRight: false, bottomLeft: false, bottomRight: false,
    },
};

interface HighlightRegion {
    sheetId: string;
    row: number;
    col: number;
    height: number;
    width: number;
}

export interface HighlightManager {
    /** 用最新 loopBlocks 同步循环块高亮区域 */
    sync(blocks: Record<string, LoopBlockConfig>): void;
    /** 用最新配置单元格同步高亮 */
    syncCells(cells: CellRange[]): void;
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
    const loopRegions: Map<string, HighlightRegion> = new Map();
    let cellRegions: HighlightRegion[] = [];
    let disposables: IDisposable[] = [];

    /** 按 sheet 分组并对一批区域应用同一高亮样式 */
    function applyGroup(
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        workbook: any,
        regions: HighlightRegion[],
        style: typeof LOOP_HIGHLIGHT_STYLE,
    ): void {
        if (regions.length === 0) return;
        const sheetGroups = new Map<string, HighlightRegion[]>();
        regions.forEach((region) => {
            const group = sheetGroups.get(region.sheetId) || [];
            group.push(region);
            sheetGroups.set(region.sheetId, group);
        });
        sheetGroups.forEach((group, sheetId) => {
            try {
                const sheet = workbook.getSheetBySheetId(sheetId);
                if (!sheet) return;
                const ranges = group.map((r) => sheet.getRange(r.row, r.col, r.height, r.width));
                disposables.push(sheet.highlightRanges(ranges, style));
            } catch {
                // sheet 可能已被删除
            }
        });
    }

    function doApply(): void {
        // 释放旧的
        disposables.forEach((d) => { try { d.dispose(); } catch { /* ignore */ } });
        disposables = [];

        if (loopRegions.size === 0 && cellRegions.length === 0) return;

        const workbook = univerAPI.getActiveWorkbook();
        if (!workbook) return;

        applyGroup(workbook, Array.from(loopRegions.values()), LOOP_HIGHLIGHT_STYLE);
        applyGroup(workbook, cellRegions, CELL_HIGHLIGHT_STYLE);
    }

    function sync(blocks: Record<string, LoopBlockConfig>): void {
        // 移除不再存在的
        const toRemove: string[] = [];
        loopRegions.forEach((_, id) => {
            if (!blocks[id]) toRemove.push(id);
        });
        toRemove.forEach((id) => loopRegions.delete(id));

        // 添加/更新存在的
        for (const [id, block] of Object.entries(blocks)) {
            loopRegions.set(id, {
                sheetId: block.sheetId,
                row: block.startRow,
                col: block.startColumn,
                height: block.endRow - block.startRow + 1,
                width: block.endColumn - block.startColumn + 1,
            });
        }

        doApply();
    }

    function syncCells(cells: CellRange[]): void {
        cellRegions = cells.map((c) => ({
            sheetId: c.sheetId,
            row: c.startRow,
            col: c.startColumn,
            height: c.endRow - c.startRow + 1,
            width: c.endColumn - c.startColumn + 1,
        }));
        doApply();
    }

    return {
        sync,
        syncCells,
        reapply: doApply,
        hasRegions: () => loopRegions.size > 0 || cellRegions.length > 0,
        dispose: () => {
            disposables.forEach((d) => { try { d.dispose(); } catch { /* ignore */ } });
            disposables = [];
            loopRegions.clear();
            cellRegions = [];
        },
    };
}
