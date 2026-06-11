/**
 * Univer 快照提取工具
 * 输出后端友好的 Excel 数据结构，可直接映射到 Apache POI API
 */

import type { FWorkbook } from '@univerjs/core/facade';

// ─── 输出类型定义 ───────────────────────────────────────────

interface ExcelWorkbook {
  sheets: ExcelSheet[];
}

interface ExcelSheet {
  id: string;
  name: string;
  /** 总行数 */
  rowCount: number;
  /** 总列数 */
  columnCount: number;
  /** 默认行高（像素） */
  defaultRowHeight: number;
  /** 默认列宽（像素） */
  defaultColumnWidth: number;
  /** 合并区域列表 */
  merges: ExcelMerge[];
  /** 单元格数据（仅包含有内容的单元格） */
  cells: ExcelCell[];
  /** 自定义行高（仅包含非默认行） */
  rows: ExcelRow[];
  /** 自定义列宽（仅包含非默认列） */
  columns: ExcelColumn[];
}

interface ExcelMerge {
  /** 起始行（0-based） */
  startRow: number;
  /** 起始列（0-based） */
  startCol: number;
  /** 行数 */
  rowSpan: number;
  /** 列数 */
  colSpan: number;
}

interface ExcelCell {
  /** 行索引（0-based） */
  row: number;
  /** 列索引（0-based） */
  col: number;
  /** A1 表示法 */
  ref: string;
  /** 单元格值 */
  value: string | number | boolean | null;
  /** 公式（不含 = 前缀） */
  formula?: string;
  /** 富文本（有分段样式时存在） */
  richText?: ExcelRichText;
  /** 单元格样式 */
  style?: ExcelStyle;
}

interface ExcelRichText {
  /** 完整纯文本 */
  text: string;
  /** 分段样式 */
  segments: Array<{
    text: string;
    style?: ExcelFont;
  }>;
}

interface ExcelStyle {
  font?: ExcelFont;
  /** 水平对齐 */
  align?: 'left' | 'center' | 'right' | 'justify' | 'distributed';
  /** 垂直对齐 */
  valign?: 'top' | 'middle' | 'bottom';
  /** 自动换行 */
  wrap?: boolean;
  /** 文字旋转角度（0-180） */
  rotation?: number;
  /** 背景填充色（#RRGGBB） */
  fill?: string;
  /** 四边边框 */
  borders?: ExcelBorders;
  /** 数字格式（如 "0.00", "#,##0", "yyyy-MM-dd"） */
  numberFormat?: string;
  /** 内边距 {top, right, bottom, left} */
  padding?: { top?: number; right?: number; bottom?: number; left?: number };
}

interface ExcelFont {
  family?: string;
  size?: number;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  strikethrough?: boolean;
  /** 字体颜色（#RRGGBB） */
  color?: string;
}

interface ExcelBorders {
  top?: ExcelBorder;
  right?: ExcelBorder;
  bottom?: ExcelBorder;
  left?: ExcelBorder;
}

interface ExcelBorder {
  /** 线型 */
  style: 'thin' | 'hair' | 'dotted' | 'dashed' | 'dashDot' | 'dashDotDot' |
         'double' | 'medium' | 'mediumDashed' | 'mediumDashDot' |
         'mediumDashDotDot' | 'slantDashDot' | 'thick';
  /** 颜色（#RRGGBB） */
  color: string;
}

interface ExcelRow {
  index: number;
  height: number;
  hidden: boolean;
}

interface ExcelColumn {
  index: number;
  width: number;
  hidden: boolean;
}

// ─── 映射表 ─────────────────────────────────────────────────

/** 水平对齐枚举映射（Univer HorizontalAlign） */
const H_ALIGN: Record<number, ExcelStyle['align']> = {
  1: 'left', 2: 'center', 3: 'right', 4: 'justify', 6: 'distributed',
};

/** 垂直对齐枚举映射（Univer VerticalAlign） */
const V_ALIGN: Record<number, ExcelStyle['valign']> = {
  1: 'top', 2: 'middle', 3: 'bottom',
};

/** 换行策略映射（Univer WrapStrategy） */
const WRAP: Record<number, boolean> = {
  0: false, 1: false, 2: false, 3: true,
};

/** 边框线型映射（Univer BorderStyleTypes → 可读名称） */
const BORDER_STYLE: Record<number, ExcelBorder['style']> = {
  1: 'thin', 2: 'hair', 3: 'dotted', 4: 'dashed',
  5: 'dashDot', 6: 'dashDotDot', 7: 'double', 8: 'medium',
  9: 'mediumDashed', 10: 'mediumDashDot', 11: 'mediumDashDotDot',
  12: 'slantDashDot', 13: 'thick',
};

// ─── 工具函数 ───────────────────────────────────────────────

function rowColToA1(row: number, col: number): string {
  let colStr = '';
  let c = col;
  while (c >= 0) {
    colStr = String.fromCharCode(65 + (c % 26)) + colStr;
    c = Math.floor(c / 26) - 1;
  }
  return `${colStr}${row + 1}`;
}

function a1ToRowCol(a1: string): [number, number] {
  const match = a1.match(/^([A-Z]+)(\d+)$/);
  if (!match) return [0, 0];
  let col = 0;
  for (const ch of match[1]) {
    col = col * 26 + (ch.charCodeAt(0) - 64);
  }
  return [parseInt(match[2]) - 1, col - 1];
}

// ─── 解析函数 ───────────────────────────────────────────────

function parseColor(cl: unknown): string | undefined {
  if (!cl || typeof cl !== 'object') return undefined;
  return (cl as Record<string, unknown>).rgb as string | undefined;
}

function parseFont(raw: Record<string, unknown>): ExcelFont | undefined {
  const font: ExcelFont = {};
  let hasValue = false;

  if (raw.ff) { font.family = raw.ff as string; hasValue = true; }
  if (raw.fs) { font.size = raw.fs as number; hasValue = true; }
  if (raw.bl === 1) { font.bold = true; hasValue = true; }
  if (raw.it === 1) { font.italic = true; hasValue = true; }
  if (raw.ul && typeof raw.ul === 'object' && (raw.ul as Record<string, unknown>).s === 1) {
    font.underline = true; hasValue = true;
  }
  if (raw.st && typeof raw.st === 'object' && (raw.st as Record<string, unknown>).s === 1) {
    font.strikethrough = true; hasValue = true;
  }
  const cl = parseColor(raw.cl);
  if (cl) { font.color = cl; hasValue = true; }

  return hasValue ? font : undefined;
}

function parseBorder(side: unknown): ExcelBorder | undefined {
  if (!side || typeof side !== 'object') return undefined;
  const s = (side as Record<string, unknown>).s as number;
  if (!s || s === 0) return undefined;
  const style = BORDER_STYLE[s];
  if (!style) return undefined;
  const cl = parseColor((side as Record<string, unknown>).cl);
  return { style, color: cl || '#000000' };
}

function parseBorders(bd: unknown): ExcelBorders | undefined {
  if (!bd || typeof bd !== 'object') return undefined;
  const raw = bd as Record<string, unknown>;
  const borders: ExcelBorders = {};
  let hasValue = false;
  const t = parseBorder(raw.t); if (t) { borders.top = t; hasValue = true; }
  const r = parseBorder(raw.r); if (r) { borders.right = r; hasValue = true; }
  const b = parseBorder(raw.b); if (b) { borders.bottom = b; hasValue = true; }
  const l = parseBorder(raw.l); if (l) { borders.left = l; hasValue = true; }
  return hasValue ? borders : undefined;
}

function parsePadding(pd: unknown): ExcelStyle['padding'] | undefined {
  if (!pd || typeof pd !== 'object') return undefined;
  const raw = pd as Record<string, unknown>;
  const result: NonNullable<ExcelStyle['padding']> = {};
  let hasValue = false;
  if (raw.t != null) { result.top = raw.t as number; hasValue = true; }
  if (raw.r != null) { result.right = raw.r as number; hasValue = true; }
  if (raw.b != null) { result.bottom = raw.b as number; hasValue = true; }
  if (raw.l != null) { result.left = raw.l as number; hasValue = true; }
  return hasValue ? result : undefined;
}

function parseStyle(raw: Record<string, unknown>): ExcelStyle | undefined {
  const style: ExcelStyle = {};
  let hasValue = false;

  const font = parseFont(raw);
  if (font) { style.font = font; hasValue = true; }

  if (raw.ht != null) {
    const align = H_ALIGN[raw.ht as number];
    if (align) { style.align = align; hasValue = true; }
  }
  if (raw.vt != null) {
    const valign = V_ALIGN[raw.vt as number];
    if (valign) { style.valign = valign; hasValue = true; }
  }
  if (raw.tb != null && WRAP[raw.tb as number]) {
    style.wrap = true; hasValue = true;
  }
  if (raw.tr && typeof raw.tr === 'object') {
    const a = (raw.tr as Record<string, unknown>).a as number;
    if (a) { style.rotation = a; hasValue = true; }
  }

  const bg = parseColor(raw.bg);
  if (bg) { style.fill = bg; hasValue = true; }

  const borders = parseBorders(raw.bd);
  if (borders) { style.borders = borders; hasValue = true; }

  if (raw.n && typeof raw.n === 'object') {
    const pattern = (raw.n as Record<string, unknown>).pattern as string;
    if (pattern) { style.numberFormat = pattern; hasValue = true; }
  }

  const padding = parsePadding(raw.pd);
  if (padding) { style.padding = padding; hasValue = true; }

  return hasValue ? style : undefined;
}

function parseRichText(docData: Record<string, unknown>): ExcelRichText | undefined {
  const body = docData.body as Record<string, unknown> | undefined;
  const dataStream = (body?.dataStream as string) || (docData.dataStream as string) || '';
  const text = dataStream.replace(/[\r\n]+$/, '');

  if (!body) return undefined;

  const rawRuns = body.textRuns as Array<Record<string, unknown>> | undefined;
  if (!rawRuns || rawRuns.length === 0) {
    // 无分段样式，不需要 richText
    return undefined;
  }

  const segments = rawRuns.map(run => {
    const start = run.st as number;
    const end = run.ed as number;
    const ts = run.ts as Record<string, unknown> | undefined;
    return {
      text: text.slice(start, end),
      style: ts ? parseFont(ts) : undefined,
    };
  });

  return { text, segments };
}

// ─── 主函数 ─────────────────────────────────────────────────

/**
 * 提取工作簿快照，输出后端友好的 Excel 数据结构
 */
export function extractWorkbookSnapshot(fWorkbook: FWorkbook): void {
  console.group('📊 [Excel 快照]');

  const snapshot = fWorkbook.save();
  const rawSheets = (snapshot as Record<string, unknown>).sheets as Record<string, Record<string, unknown>> | undefined;
  const globalStyles = (snapshot as Record<string, unknown>).styles as Record<string, Record<string, unknown>> | undefined;

  if (!rawSheets) {
    console.log('⚠️ 无工作表数据');
    console.groupEnd();
    return;
  }

  const workbook: ExcelWorkbook = { sheets: [] };

  for (const [sheetId, sheetData] of Object.entries(rawSheets)) {
    const sheet: ExcelSheet = {
      id: sheetId,
      name: (sheetData.name as string) || 'Sheet',
      rowCount: (sheetData.rowCount as number) ?? 1000,
      columnCount: (sheetData.columnCount as number) ?? 26,
      defaultRowHeight: (sheetData.defaultRowHeight as number) ?? 24,
      defaultColumnWidth: (sheetData.defaultColumnWidth as number) ?? 88,
      merges: [],
      cells: [],
      rows: [],
      columns: [],
    };

    // 合并区域
    const mergeData = sheetData.mergeData as Array<Record<string, number>> | undefined;
    if (mergeData) {
      for (const m of mergeData) {
        sheet.merges.push({
          startRow: m.startRow,
          startCol: m.startColumn,
          rowSpan: m.endRow - m.startRow + 1,
          colSpan: m.endColumn - m.startColumn + 1,
        });
      }
    }

    // slave 集合（跳过合并区域内的非主单元格）
    const slavePositions = new Set<string>();
    for (const m of sheet.merges) {
      for (let r = m.startRow; r < m.startRow + m.rowSpan; r++) {
        for (let c = m.startCol; c < m.startCol + m.colSpan; c++) {
          if (r !== m.startRow || c !== m.startCol) {
            slavePositions.add(`${r},${c}`);
          }
        }
      }
    }

    // 单元格数据
    const cellData = sheetData.cellData as Record<string, Record<string, Record<string, unknown>>> | undefined;
    if (cellData) {
      for (const [rowStr, rowData] of Object.entries(cellData)) {
        const row = parseInt(rowStr);
        for (const [colStr, rawCell] of Object.entries(rowData)) {
          const col = parseInt(colStr);
          if (slavePositions.has(`${row},${col}`)) continue;

          // 解析样式（处理全局样式引用）
          let resolvedStyle: Record<string, unknown> | undefined;
          const s = rawCell.s;
          if (s) {
            if (typeof s === 'string') {
              resolvedStyle = globalStyles?.[s];
            } else if (typeof s === 'object') {
              resolvedStyle = s as Record<string, unknown>;
            }
          }

          // 解析富文本
          let richText: ExcelRichText | undefined;
          let richTextValue: string | null = null;
          if (rawCell.p && typeof rawCell.p === 'object') {
            richText = parseRichText(rawCell.p as Record<string, unknown>);
            richTextValue = richText?.text || null;
          }

          const value = (rawCell.v != null && rawCell.v !== '')
            ? rawCell.v as string | number | boolean
            : richTextValue;

          const formula = (typeof rawCell.f === 'string' && rawCell.f.startsWith('='))
            ? rawCell.f.slice(1) as string
            : undefined;

          const style = resolvedStyle ? parseStyle(resolvedStyle) : undefined;

          // 跳过完全空的单元格
          if (value == null && !formula && !richText && !style) continue;

          const cell: ExcelCell = {
            row,
            col,
            ref: rowColToA1(row, col),
            value: value ?? null,
          };
          if (formula) cell.formula = formula;
          if (richText) cell.richText = richText;
          if (style) cell.style = style;

          sheet.cells.push(cell);
        }
      }
    }

    // 按行列排序
    sheet.cells.sort((a, b) => a.row - b.row || a.col - b.col);

    // 行配置
    const rowData = sheetData.rowData as Record<string, unknown> | undefined;
    if (rowData) {
      for (const [idx, r] of Object.entries(rowData)) {
        const raw = r as Record<string, unknown>;
        sheet.rows.push({
          index: parseInt(idx),
          height: (raw.h as number) ?? sheet.defaultRowHeight,
          hidden: raw.hd === 1,
        });
      }
    }

    // 列配置
    const colData = sheetData.columnData as Record<string, unknown> | undefined;
    if (colData) {
      for (const [idx, c] of Object.entries(colData)) {
        const raw = c as Record<string, unknown>;
        sheet.columns.push({
          index: parseInt(idx),
          width: (raw.w as number) ?? sheet.defaultColumnWidth,
          hidden: raw.hd === 1,
        });
      }
    }

    workbook.sheets.push(sheet);
  }

  // 完整 JSON 输出
  console.log(JSON.stringify(workbook, null, 2));
  console.groupEnd();
}
