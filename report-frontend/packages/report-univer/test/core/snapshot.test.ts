import { extractSnapshot } from '@/core/snapshot';
import type { LoopBlockConfig } from '@/types';

/**
 * 构造一个 fake workbook：只暴露 extractSnapshot 依赖的 save() 方法，
 * 返回模拟的 Univer snapshot 对象。无需真实 Univer 实例。
 */
function fakeWorkbook(snapshot: Record<string, unknown>): { save: () => Record<string, unknown> } {
  return { save: () => snapshot };
}

/** 构造单个 sheet 的最小 snapshot 片段 */
function sheet(id: string, data: Record<string, unknown> = {}): Record<string, unknown> {
  return { id, ...data };
}

describe('extractSnapshot', () => {
  test('无 sheets 时返回空结构', () => {
    const wb = fakeWorkbook({});
    expect(extractWorkbook(wb)).toEqual({ sheets: [] });
  });

  test('缺失元信息时回退默认值', () => {
    const wb = fakeWorkbook({ sheets: { s1: { name: 'S1' } } });
    const result = extractWorkbook(wb);
    expect(result.sheets).toHaveLength(1);
    const s = result.sheets[0];
    expect(s.id).toBe('s1');
    expect(s.name).toBe('S1');
    expect(s.rowCount).toBe(1000);
    expect(s.columnCount).toBe(26);
    expect(s.defaultRowHeight).toBe(24);
    expect(s.defaultColumnWidth).toBe(88);
  });

  test('单元格值与 A1 引用正确映射（含 Z/AA 边界）', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          cellData: {
            0: { 0: { v: 'A1' }, 25: { v: 'Z1' } },
            1: { 26: { v: 'AA2' } },
          },
        }),
      },
    });
    const cells = extractWorkbook(wb).sheets[0].cells;
    expect(cells).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ row: 0, col: 0, ref: 'A1', value: 'A1' }),
        expect.objectContaining({ row: 0, col: 25, ref: 'Z1', value: 'Z1' }),
        expect.objectContaining({ row: 1, col: 26, ref: 'AA2', value: 'AA2' }),
      ]),
    );
  });

  test('公式去除等号前缀；无等号的公式不识别为公式', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          cellData: {
            0: {
              0: { v: 10, f: '=SUM(A1:A2)' },
              1: { v: 'x', f: 'NOEQ' },
            },
          },
        }),
      },
    });
    const cells = extractWorkbook(wb).sheets[0].cells;
    const withEq = cells.find((c) => c.col === 0)!;
    const withoutEq = cells.find((c) => c.col === 1)!;
    expect(withEq.formula).toBe('SUM(A1:A2)');
    expect(withoutEq.formula).toBeUndefined();
  });

  test('全局样式引用（s 为字符串）被解析', () => {
    const wb = fakeWorkbook({
      styles: { st1: { bg: { rgb: '#FFEECC' } } },
      sheets: {
        s1: sheet('s1', {
          cellData: { 0: { 0: { v: 'hi', s: 'st1' } } },
        }),
      },
    });
    const cell = extractWorkbook(wb).sheets[0].cells[0];
    expect(cell.style?.fill).toBe('#FFEECC');
  });

  test('完全空单元格被跳过（无值/无样式/无属性）', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          cellData: { 0: { 0: { v: 'keep' }, 1: { s: {} } } },
        }),
      },
    });
    const cells = extractWorkbook(wb).sheets[0].cells;
    expect(cells).toHaveLength(1);
    expect(cells[0].ref).toBe('A1');
  });

  test('富文本被解析并取值', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          cellData: {
            0: {
              0: {
                p: {
                  body: {
                    dataStream: 'Hello\r\n',
                    textRuns: [{ st: 0, ed: 5, ts: { bl: 1 } }],
                  },
                },
              },
            },
          },
        }),
      },
    });
    const cell = extractWorkbook(wb).sheets[0].cells[0];
    expect(cell.value).toBe('Hello');
    expect(cell.richText?.text).toBe('Hello');
    expect(cell.richText?.segments[0].style?.bold).toBe(true);
  });

  test('合并区域：slave 单元格被跳过，主单元格携带 props 与 rowSpan/colSpan', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          mergeData: [{ startRow: 0, startColumn: 0, endRow: 1, endColumn: 1 }],
          cellData: {
            0: { 0: { v: 'master' }, 1: { v: 'slave-r' } },
            1: { 0: { v: 'slave-b' }, 1: { v: 'slave-br' } },
          },
        }),
      },
    });
    const s = extractWorkbook(wb, {
      mergeProps: { 'merge:s1:0:0:1:1': [{ kind: 'mergeProp' }] },
    }).sheets[0];
    expect(s.cells).toHaveLength(1); // 仅 master 输出
    const merge = s.merges[0];
    expect(merge).toEqual(
      expect.objectContaining({ startRow: 0, startCol: 0, rowSpan: 2, colSpan: 2 }),
    );
    expect(merge.props).toEqual([{ kind: 'mergeProp' }]);
  });

  test('合并边框从 slave 收集（右边框取最右列、下边框取最底行）', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          mergeData: [{ startRow: 0, startColumn: 0, endRow: 1, endColumn: 1 }],
          cellData: {
            0: {
              0: { v: 'm' },
              1: { s: { bd: { r: { s: 1, cl: { rgb: '#FF0000' } } } } },
            },
            1: {
              0: { s: { bd: { b: { s: 1, cl: { rgb: '#00FF00' } } } } },
            },
          },
        }),
      },
    });
    const cell = extractWorkbook(wb).sheets[0].cells[0];
    expect(cell.style?.borders?.right).toEqual({ style: 'thin', color: '#FF0000' });
    expect(cell.style?.borders?.bottom).toEqual({ style: 'thin', color: '#00FF00' });
  });

  test('cellProps 按 sheetId:row:col 挂载到对应单元格', () => {
    const wb = fakeWorkbook({
      sheets: { s1: sheet('s1', { cellData: { 0: { 0: { v: 'x' } } } }) },
    });
    const cell = extractWorkbook(wb, {
      cellProps: { 's1:0:0': [{ kind: 'cellProp' }], 's1:9:9': [{ kind: 'other' }] },
    }).sheets[0].cells[0];
    expect(cell.props).toEqual([{ kind: 'cellProp' }]);
  });

  test('行列配置（含 hidden 标记）正确映射', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          defaultRowHeight: 30,
          defaultColumnWidth: 100,
          rowData: { 0: { h: 40 }, 1: { hd: 1 } },
          columnData: { 0: { w: 120 }, 2: { hd: 1 } },
        }),
      },
    });
    const s = extractWorkbook(wb).sheets[0];
    expect(s.rows).toEqual([
      { index: 0, height: 40, hidden: false },
      { index: 1, height: 30, hidden: true },
    ]);
    expect(s.columns).toEqual([
      { index: 0, width: 120, hidden: false },
      { index: 2, width: 100, hidden: true },
    ]);
  });

  test('loopBlocks 按 sheetId 过滤并挂载 loopBlockProps', () => {
    const blocks: LoopBlockConfig[] = [
      {
        id: 'b1',
        sheetId: 's1',
        startRow: 0,
        startColumn: 0,
        endRow: 2,
        endColumn: 1,
        label: 'L1',
      },
      { id: 'b2', sheetId: 's2', startRow: 0, startColumn: 0, endRow: 1, endColumn: 1 },
    ];
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {}),
        s2: sheet('s2', {}),
      },
    });
    const result = extractWorkbook(wb, {
      loopBlocks: blocks,
      loopBlockProps: { b1: [{ kind: 'loopProp' }] },
    });
    const s1 = result.sheets.find((x) => x.id === 's1')!;
    const s2 = result.sheets.find((x) => x.id === 's2')!;
    expect(s1.loopBlocks).toEqual([
      expect.objectContaining({
        id: 'b1',
        startRow: 0,
        startCol: 0,
        endRow: 2,
        endCol: 1,
        props: [{ kind: 'loopProp' }],
      }),
    ]);
    expect(s2.loopBlocks?.[0].id).toBe('b2');
    expect(s2.loopBlocks?.[0].props).toBeUndefined();
  });

  test('单元格按行列升序排序', () => {
    const wb = fakeWorkbook({
      sheets: {
        s1: sheet('s1', {
          cellData: {
            1: { 1: { v: 'B2' }, 0: { v: 'A2' } },
            0: { 0: { v: 'A1' } },
          },
        }),
      },
    });
    const refs = extractWorkbook(wb).sheets[0].cells.map((c) => c.ref);
    expect(refs).toEqual(['A1', 'A2', 'B2']);
  });
});

/** 便捷封装：避免每个用例重复 generic 写法 */
function extractWorkbook(
  wb: { save: () => Record<string, unknown> },
  options?: Parameters<typeof extractSnapshot>[1],
) {
  return extractSnapshot(wb, options);
}
