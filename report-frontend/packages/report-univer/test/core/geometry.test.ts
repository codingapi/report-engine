import { findBlockAtCell } from '@/core/geometry';
import type { LoopBlockConfig } from '@/types';

const blocks: Record<string, LoopBlockConfig> = {
  b1: { id: 'b1', sheetId: 's1', startRow: 0, startColumn: 0, endRow: 3, endColumn: 2 },
  b2: { id: 'b2', sheetId: 's1', startRow: 10, startColumn: 0, endRow: 12, endColumn: 5 },
  b3: { id: 'b3', sheetId: 's2', startRow: 0, startColumn: 0, endRow: 1, endColumn: 1 },
};

describe('findBlockAtCell', () => {
  test('命中区域内部（含边界）返回该块', () => {
    expect(findBlockAtCell(blocks, 's1', 0, 0)?.id).toBe('b1');
    expect(findBlockAtCell(blocks, 's1', 3, 2)?.id).toBe('b1'); // 右下角边界
  });

  test('越过边界返回 null', () => {
    expect(findBlockAtCell(blocks, 's1', 4, 0)).toBeNull(); // 行越界
    expect(findBlockAtCell(blocks, 's1', 0, 3)).toBeNull(); // 列越界
    expect(findBlockAtCell(blocks, 's1', 9, 0)).toBeNull(); // 两块之间空隙
  });

  test('不同 sheetId 返回 null', () => {
    expect(findBlockAtCell(blocks, 'sX', 0, 0)).toBeNull();
    expect(findBlockAtCell(blocks, 's2', 10, 0)).toBeNull(); // b2 在 s1
  });

  test('空 blocks 返回 null', () => {
    expect(findBlockAtCell({}, 's1', 0, 0)).toBeNull();
  });

  test('多个块匹配时返回第一个（按 Object.values 顺序）', () => {
    expect(findBlockAtCell(blocks, 's1', 11, 1)?.id).toBe('b2');
  });
});
