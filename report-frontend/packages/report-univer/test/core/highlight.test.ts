import { createHighlightManager } from '@/core/highlight';
import type { LoopBlockConfig, CellRange } from '@/types';

interface Disposable {
  dispose: () => void;
}
interface SheetMock {
  getRange: ReturnType<typeof rs.fn>;
  highlightRanges: ReturnType<typeof rs.fn>;
}

/** 构造 mock univerAPI，记录每次 highlightRanges 的入参，返回可追踪 disposable */
function makeUniver(sheetsById: Record<string, SheetMock>) {
  const disposables: Disposable[] = [];
  const workbook = {
    getSheetBySheetId: rs.fn((id: string) => sheetsById[id] ?? null),
  };
  return {
    api: { getActiveWorkbook: rs.fn(() => workbook) } as never,
    disposables,
    sheetsById,
  };
}

function sheetMock(): { sheet: SheetMock; calls: unknown[] } {
  const calls: unknown[] = [];
  const sheet: SheetMock = {
    getRange: rs.fn((r: number, c: number, h: number, w: number) => ({ r, c, h, w })),
    highlightRanges: rs.fn((ranges: unknown[], style: unknown) => {
      const d = { dispose: rs.fn() };
      calls.push({ ranges, style });
      return d;
    }),
  };
  return { sheet, calls };
}

describe('createHighlightManager', () => {
  test('初始无区域时 hasRegions 为 false 且不调用 workbook', () => {
    const { api, sheetsById } = makeUniver({});
    const hm = createHighlightManager(api);
    expect(hm.hasRegions()).toBe(false);
    hm.sync({});
    expect(sheetsById).toEqual({});
    hm.dispose();
  });

  test('sync 一个循环块：应用区域、hasRegions 为 true、区域参数正确', () => {
    const { sheet, calls } = sheetMock();
    const { api } = makeUniver({ s1: sheet });
    const hm = createHighlightManager(api);
    const blocks: Record<string, LoopBlockConfig> = {
      b1: { id: 'b1', sheetId: 's1', startRow: 0, startColumn: 0, endRow: 2, endColumn: 1 },
    };
    hm.sync(blocks);
    expect(hm.hasRegions()).toBe(true);
    expect(calls).toHaveLength(1);
    expect(sheet.getRange).toHaveBeenCalledWith(0, 0, 3, 2); // height=3 width=2
    expect(calls[0]).toMatchObject({ style: expect.objectContaining({ stroke: '#1677ff' }) });
    hm.dispose();
  });

  test('sync 移除已删除的块：旧区域被 dispose、不再应用', () => {
    const { sheet, calls } = sheetMock();
    const { api } = makeUniver({ s1: sheet });
    const hm = createHighlightManager(api);
    hm.sync({
      b1: { id: 'b1', sheetId: 's1', startRow: 0, startColumn: 0, endRow: 0, endColumn: 0 },
    });
    const firstDisposable = calls[0] as unknown as { ranges: unknown };
    expect(firstDisposable).toBeTruthy();
    hm.sync({}); // 清空
    expect(hm.hasRegions()).toBe(false);
    hm.dispose();
  });

  test('syncCells 应用配置单元格高亮（CELL 样式）', () => {
    const { sheet, calls } = sheetMock();
    const { api } = makeUniver({ s1: sheet });
    const hm = createHighlightManager(api);
    const cells: CellRange[] = [
      { sheetId: 's1', startRow: 1, startColumn: 1, endRow: 1, endColumn: 1 },
    ];
    hm.syncCells(cells);
    expect(hm.hasRegions()).toBe(true);
    expect(sheet.getRange).toHaveBeenCalledWith(1, 1, 1, 1);
    expect(calls[0]).toMatchObject({
      style: expect.objectContaining({ strokeDash: 0, fill: 'rgba(22, 119, 255, 0.16)' }),
    });
    hm.dispose();
  });

  test('syncCells 按 sheet 分组调用', () => {
    const a = sheetMock();
    const b = sheetMock();
    const { api } = makeUniver({ s1: a.sheet, s2: b.sheet });
    const hm = createHighlightManager(api);
    hm.syncCells([
      { sheetId: 's1', startRow: 0, startColumn: 0, endRow: 0, endColumn: 0 },
      { sheetId: 's2', startRow: 0, startColumn: 0, endRow: 0, endColumn: 0 },
    ]);
    expect(a.sheet.highlightRanges).toHaveBeenCalledTimes(1);
    expect(b.sheet.highlightRanges).toHaveBeenCalledTimes(1);
    hm.dispose();
  });

  test('sheet 不存在时不抛错', () => {
    const { api } = makeUniver({}); // 无 sheet
    const hm = createHighlightManager(api);
    expect(() =>
      hm.sync({
        b1: { id: 'b1', sheetId: 'missing', startRow: 0, startColumn: 0, endRow: 0, endColumn: 0 },
      }),
    ).not.toThrow();
    hm.dispose();
  });

  test('reapply 重新应用全部区域（先释放旧 disposable）', () => {
    const { sheet, calls } = sheetMock();
    const { api } = makeUniver({ s1: sheet });
    const hm = createHighlightManager(api);
    hm.sync({
      b1: { id: 'b1', sheetId: 's1', startRow: 0, startColumn: 0, endRow: 0, endColumn: 0 },
    });
    expect(calls).toHaveLength(1);
    hm.reapply();
    expect(calls).toHaveLength(2); // 重新应用一次
    hm.dispose();
  });

  test('dispose 清空区域与 disposable，hasRegions 归零', () => {
    const { sheet } = sheetMock();
    const { api } = makeUniver({ s1: sheet });
    const hm = createHighlightManager(api);
    hm.sync({
      b1: { id: 'b1', sheetId: 's1', startRow: 0, startColumn: 0, endRow: 0, endColumn: 0 },
    });
    hm.dispose();
    expect(hm.hasRegions()).toBe(false);
  });
});
