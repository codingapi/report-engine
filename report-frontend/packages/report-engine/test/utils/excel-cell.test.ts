import {
  colToLetter,
  letterToCol,
  parseCellKey,
  makeCellKey,
  cellA1,
  describeRange,
} from '@/utils/excel-cell';

describe('colToLetter', () => {
  test('单字母边界', () => {
    expect(colToLetter(0)).toBe('A');
    expect(colToLetter(1)).toBe('B');
    expect(colToLetter(25)).toBe('Z');
  });
  test('双字母进位', () => {
    expect(colToLetter(26)).toBe('AA');
    expect(colToLetter(27)).toBe('AB');
    expect(colToLetter(51)).toBe('AZ');
    expect(colToLetter(701)).toBe('ZZ');
  });
  test('三字母进位', () => {
    expect(colToLetter(702)).toBe('AAA');
  });
});

describe('letterToCol', () => {
  test('单/双/三字母还原', () => {
    expect(letterToCol('A')).toBe(0);
    expect(letterToCol('Z')).toBe(25);
    expect(letterToCol('AA')).toBe(26);
    expect(letterToCol('AZ')).toBe(51);
    expect(letterToCol('ZZ')).toBe(701);
    expect(letterToCol('AAA')).toBe(702);
  });
  test('colToLetter ↔ letterToCol 往返', () => {
    for (const c of [0, 1, 25, 26, 51, 701, 702, 728]) {
      expect(letterToCol(colToLetter(c))).toBe(c);
    }
  });
});

describe('cellKey 解析/构造', () => {
  test('makeCellKey 拼接 "sheetId:row:col"', () => {
    expect(makeCellKey('s1', 0, 0)).toBe('s1:0:0');
    expect(makeCellKey('sheet-uuid', 12, 5)).toBe('sheet-uuid:12:5');
  });
  test('parseCellKey 还原', () => {
    expect(parseCellKey('s1:3:7')).toEqual({ sheetId: 's1', row: 3, col: 7 });
  });
  test('往返一致', () => {
    const pos = { sheetId: 's1', row: 100, col: 26 };
    expect(parseCellKey(makeCellKey(pos.sheetId, pos.row, pos.col))).toEqual(pos);
  });
});

describe('cellA1 / describeRange', () => {
  test('cellA1: (row,col) → A1 引用', () => {
    expect(cellA1(0, 0)).toBe('A1');
    expect(cellA1(0, 25)).toBe('Z1');
    expect(cellA1(1, 26)).toBe('AA2');
  });
  test('describeRange: A1:C4 形式', () => {
    expect(describeRange(0, 0, 3, 2)).toBe('A1:C4');
    expect(describeRange(0, 26, 0, 26)).toBe('AA1:AA1');
  });
});
