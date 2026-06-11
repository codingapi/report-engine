import type { LoopBlockConfig } from '../types';

/**
 * 在 loopBlocks 中查找包含指定行列的循环块
 */
export function findBlockAtCell(
    blocks: Record<string, LoopBlockConfig>,
    sheetId: string,
    row: number,
    col: number,
): LoopBlockConfig | null {
    for (const block of Object.values(blocks)) {
        if (
            block.sheetId === sheetId &&
            row >= block.startRow &&
            row <= block.endRow &&
            col >= block.startColumn &&
            col <= block.endColumn
        ) {
            return block;
        }
    }
    return null;
}
