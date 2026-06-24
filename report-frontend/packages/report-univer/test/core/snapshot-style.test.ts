import { extractSnapshot } from '@/core/snapshot';
import type { ExcelStyle, ExcelBorderStyle } from '@/types';

/**
 * 样式解析专项测试：snapshot.ts 的 parseStyle / parseFont / parseBorder /
 * parsePadding / parseColor 把 Univer 内部样式翻译成后端 POI 友好结构，
 * 枚举映射与条件判定（s===1、tb=3 等）是最易错点，这里按分支穷尽覆盖。
 */

function fakeWorkbook(snapshot: Record<string, unknown>) {
  return { save: () => snapshot };
}

/** 把一个 inline 样式对象喂给单个单元格，返回解析后的 ExcelStyle（可能 undefined） */
function styleOf(rawStyle: Record<string, unknown>): ExcelStyle | undefined {
  const wb = fakeWorkbook({
    sheets: {
      s1: {
        id: 's1',
        cellData: { 0: { 0: { s: rawStyle } } },
      },
    },
  });
  const cells = extractSnapshot(wb).sheets[0].cells;
  // 有 style 的单元格即使无值也会输出
  return cells[0]?.style;
}

describe('parseFont', () => {
  test('完整字体属性全部映射', () => {
    const style = styleOf({
      ff: 'Arial',
      fs: 14,
      bl: 1,
      it: 1,
      ul: { s: 1 },
      st: { s: 1 },
      cl: { rgb: '#FF0000' },
    });
    expect(style?.font).toEqual({
      family: 'Arial',
      size: 14,
      bold: true,
      italic: true,
      underline: true,
      strikethrough: true,
      color: '#FF0000',
    });
  });

  test('下划线/删除线仅当 s===1 时启用，s 为 0 或缺省时忽略', () => {
    expect(styleOf({ ul: { s: 0 }, st: { s: 0 } })?.font).toBeUndefined();
    expect(styleOf({ ul: {} })?.font).toBeUndefined();
  });

  test('空样式对象不产生 font', () => {
    expect(styleOf({})?.font).toBeUndefined();
  });
});

describe('parseStyle: 对齐 / 换行 / 旋转', () => {
  test('水平对齐枚举 ht 全量映射，未映射值不设', () => {
    const cases: Array<[number, ExcelStyle['align']]> = [
      [1, 'left'],
      [2, 'center'],
      [3, 'right'],
      [4, 'justify'],
      [6, 'distributed'],
    ];
    for (const [ht, align] of cases) {
      expect(styleOf({ ht })?.align).toBe(align);
    }
    // ht=5 不在映射表里 → 不设 align
    expect(styleOf({ ht: 5 })?.align).toBeUndefined();
  });

  test('垂直对齐枚举 vt 全量映射', () => {
    expect(styleOf({ vt: 1 })?.valign).toBe('top');
    expect(styleOf({ vt: 2 })?.valign).toBe('middle');
    expect(styleOf({ vt: 3 })?.valign).toBe('bottom');
  });

  test('自动换行仅 tb===3 时启用', () => {
    for (const tb of [0, 1, 2]) {
      expect(styleOf({ tb })?.wrap).toBeUndefined();
    }
    expect(styleOf({ tb: 3 })?.wrap).toBe(true);
  });

  test('文字旋转角度取 tr.a', () => {
    expect(styleOf({ tr: { a: 45 } })?.rotation).toBe(45);
    expect(styleOf({ tr: {} })?.rotation).toBeUndefined();
  });
});

describe('parseStyle: 填充 / 数字格式 / 内边距', () => {
  test('背景填充取 bg.rgb', () => {
    expect(styleOf({ bg: { rgb: '#EEEEEE' } })?.fill).toBe('#EEEEEE');
  });

  test('bg 非对象时不产生 fill', () => {
    expect(styleOf({ bg: '#fff' })?.fill).toBeUndefined();
  });

  test('数字格式取 n.pattern', () => {
    expect(styleOf({ n: { pattern: '#,##0.00' } })?.numberFormat).toBe('#,##0.00');
    expect(styleOf({ n: {} })?.numberFormat).toBeUndefined();
  });

  test('内边距各边分别取值', () => {
    expect(styleOf({ pd: { t: 1, r: 2, b: 3, l: 4 } })?.padding).toEqual({
      top: 1,
      right: 2,
      bottom: 3,
      left: 4,
    });
  });

  test('内边距部分缺失时只设存在的边', () => {
    expect(styleOf({ pd: { t: 5, l: 8 } })?.padding).toEqual({ top: 5, left: 8 });
  });

  test('pd 非对象时不产生 padding', () => {
    expect(styleOf({ pd: null })?.padding).toBeUndefined();
  });
});

describe('parseBorder', () => {
  const BORDER_CASES: Array<[number, ExcelBorderStyle]> = [
    [1, 'thin'],
    [2, 'hair'],
    [3, 'dotted'],
    [4, 'dashed'],
    [5, 'dashDot'],
    [6, 'dashDotDot'],
    [7, 'double'],
    [8, 'medium'],
    [9, 'mediumDashed'],
    [10, 'mediumDashDot'],
    [11, 'mediumDashDotDot'],
    [12, 'slantDashDot'],
    [13, 'thick'],
  ];

  test('全部 13 种边框线型枚举正确映射', () => {
    for (const [s, styleName] of BORDER_CASES) {
      const style = styleOf({ bd: { t: { s, cl: { rgb: '#000000' } } } });
      expect(style?.borders?.top).toEqual({ style: styleName, color: '#000000' });
    }
  });

  test('s===0 或缺省时该边框不设', () => {
    expect(styleOf({ bd: { t: { s: 0 } } })?.borders).toBeUndefined();
    expect(styleOf({ bd: { t: {} } })?.borders).toBeUndefined();
  });

  test('未映射的 s 值不产生边框', () => {
    expect(styleOf({ bd: { t: { s: 99, cl: { rgb: '#000' } } } })?.borders).toBeUndefined();
  });

  test('颜色缺省时回退 #000000', () => {
    const style = styleOf({ bd: { r: { s: 1 } } });
    expect(style?.borders?.right).toEqual({ style: 'thin', color: '#000000' });
  });

  test('四边边框独立解析', () => {
    const style = styleOf({
      bd: {
        t: { s: 1, cl: { rgb: '#111111' } },
        r: { s: 8, cl: { rgb: '#222222' } },
        b: { s: 13, cl: { rgb: '#333333' } },
        l: { s: 7, cl: { rgb: '#444444' } },
      },
    });
    expect(style?.borders).toEqual({
      top: { style: 'thin', color: '#111111' },
      right: { style: 'medium', color: '#222222' },
      bottom: { style: 'thick', color: '#333333' },
      left: { style: 'double', color: '#444444' },
    });
  });
});

describe('parseStyle: 整体输出形态', () => {
  test('所有样式项齐备时整体结构完整', () => {
    const style = styleOf({
      ff: 'Arial',
      fs: 12,
      bl: 1,
      cl: { rgb: '#AA0000' },
      ht: 2,
      vt: 2,
      tb: 3,
      tr: { a: 90 },
      bg: { rgb: '#BBFFCC' },
      n: { pattern: '0.00' },
      pd: { t: 2, r: 2, b: 2, l: 2 },
      bd: { t: { s: 1, cl: { rgb: '#000000' } } },
    });
    expect(style).toEqual({
      font: { family: 'Arial', size: 12, bold: true, color: '#AA0000' },
      align: 'center',
      valign: 'middle',
      wrap: true,
      rotation: 90,
      fill: '#BBFFCC',
      numberFormat: '0.00',
      padding: { top: 2, right: 2, bottom: 2, left: 2 },
      borders: { top: { style: 'thin', color: '#000000' } },
    });
  });

  test('无任何可识别样式字段时 style 为 undefined（单元格被跳过）', () => {
    expect(styleOf({ ht: 5, unknownField: true })).toBeUndefined();
  });
});
