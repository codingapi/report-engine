/**
 * Excel 单元格坐标工具：列号↔字母、cellKey 解析/构造、A1 描述。
 * 统一前端各处分散的实现（colToLetter / split(':') 等）。
 */

/** 列号 → 列字母（0→A, 25→Z, 26→AA） */
export function colToLetter(col: number): string {
  let str = '';
  let c = col;
  while (c >= 0) {
    str = String.fromCharCode(65 + (c % 26)) + str;
    c = Math.floor(c / 26) - 1;
  }
  return str;
}

/** 列字母 → 列号（A→0, AA→26） */
export function letterToCol(letter: string): number {
  let col = 0;
  for (let i = 0; i < letter.length; i++) {
    col = col * 26 + (letter.charCodeAt(i) - 64);
  }
  return col - 1;
}

export interface CellPos {
  sheetId: string;
  row: number;
  col: number;
}

/** 解析单元格键 "sheetId:row:col" */
export function parseCellKey(key: string): CellPos {
  const [sheetId, row, col] = key.split(':');
  return { sheetId, row: parseInt(row, 10), col: parseInt(col, 10) };
}

/** 构造单元格键 "sheetId:row:col" */
export function makeCellKey(sheetId: string, row: number, col: number): string {
  return `${sheetId}:${row}:${col}`;
}

/** (row, col) → A1 引用，如 (0,0)→"A1" */
export function cellA1(row: number, col: number): string {
  return `${colToLetter(col)}${row + 1}`;
}

/** 区域描述 A1:C4 */
export function describeRange(startRow: number, startCol: number, endRow: number, endCol: number): string {
  return `${cellA1(startRow, startCol)}:${cellA1(endRow, endCol)}`;
}
