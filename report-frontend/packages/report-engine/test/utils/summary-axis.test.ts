import {
  summaryAxis,
  summaryCellRC,
  crossPosOf,
  mainPosOf,
  summaryHit,
} from '@/utils/summary-axis';
import type { SummaryRow } from '@/types';

const vertical: SummaryRow = {
  id: 's1',
  mainPos: 5,
  crossFrom: 1,
  crossTo: 3,
  axis: 'VERTICAL',
  cells: [],
} as unknown as SummaryRow;

const horizontal: SummaryRow = {
  id: 's2',
  mainPos: 4,
  crossFrom: 1,
  crossTo: 3,
  axis: 'HORIZONTAL',
  cells: [],
} as unknown as SummaryRow;

describe('summaryAxis — 缺省回退 VERTICAL', () => {
  test('axis 为 undefined 时回退 VERTICAL', () => {
    expect(summaryAxis({ axis: undefined } as Pick<SummaryRow, 'axis'>)).toBe('VERTICAL');
  });
  test('显式 HORIZONTAL', () => {
    expect(summaryAxis({ axis: 'HORIZONTAL' } as Pick<SummaryRow, 'axis'>)).toBe('HORIZONTAL');
  });
});

describe('summaryCellRC — 落格坐标', () => {
  test('纵向：row=mainPos, col=crossPos', () => {
    expect(summaryCellRC(vertical, 2)).toEqual({ row: 5, col: 2 });
  });
  test('横向：row=crossPos, col=mainPos', () => {
    expect(summaryCellRC(horizontal, 2)).toEqual({ row: 2, col: 4 });
  });
});

describe('crossPosOf / mainPosOf — 轴向取值', () => {
  test('纵向取列/行', () => {
    expect(crossPosOf('VERTICAL', 7, 9)).toBe(9);
    expect(mainPosOf('VERTICAL', 7, 9)).toBe(7);
  });
  test('横向取行/列', () => {
    expect(crossPosOf('HORIZONTAL', 7, 9)).toBe(7);
    expect(mainPosOf('HORIZONTAL', 7, 9)).toBe(9);
  });
});

describe('summaryHit — 命中判定', () => {
  test('纵向命中：行=mainPos 且列在 [crossFrom,crossTo]', () => {
    expect(summaryHit(vertical, 5, 1)).toBe(true);
    expect(summaryHit(vertical, 5, 3)).toBe(true);
    expect(summaryHit(vertical, 5, 0)).toBe(false); // 列越界
    expect(summaryHit(vertical, 5, 4)).toBe(false);
    expect(summaryHit(vertical, 6, 2)).toBe(false); // 行不符
  });
  test('横向命中：列=mainPos 且行在 [crossFrom,crossTo]', () => {
    expect(summaryHit(horizontal, 1, 4)).toBe(true);
    expect(summaryHit(horizontal, 3, 4)).toBe(true);
    expect(summaryHit(horizontal, 0, 4)).toBe(false); // 行越界
    expect(summaryHit(horizontal, 2, 5)).toBe(false); // 列不符
  });
});
