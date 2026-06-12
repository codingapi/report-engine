/**
 * Univer 快照提取工具
 * 输出后端友好的 Excel 数据结构，可直接映射到 Apache POI API
 */

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type FWorkbook = any;
import type { CellProp, CellPropStore, LoopBlock } from './univer-test-props';
import { makeCellKey, makeMergeKey } from './univer-test-props';

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
  /** 循环块列表 */
  loopBlocks?: ExcelLoopBlock[];
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
  /** 合并区域属性 */
  props?: CellProp[];
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
  /** 单元格属性绑定 */
  props?: CellProp[];
}

interface ExcelLoopBlock {
  /** 循环块 ID */
  id: string;
  /** 标签 */
  label: string;
  /** 循环变量 "tableName.fieldName" */
  loopVariable: string;
  /** 起始行（0-based） */
  startRow: number;
  /** 起始列（0-based） */
  startCol: number;
  /** 结束行（0-based） */
  endRow: number;
  /** 结束列（0-based） */
  endCol: number;
  /** 循环块属性 */
  props?: CellProp[];
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
 * @param fWorkbook Univer 工作簿实例
 * @param propStore 属性绑定存储（可选）
 * @param loopBlocks 循环块列表（可选）
 */
export function extractWorkbookSnapshot(
  fWorkbook: FWorkbook,
  propStore?: CellPropStore,
  loopBlocks?: LoopBlock[],
): void {
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
        const merge: ExcelMerge = {
          startRow: m.startRow,
          startCol: m.startColumn,
          rowSpan: m.endRow - m.startRow + 1,
          colSpan: m.endColumn - m.startColumn + 1,
        };
        // 查找合并区域属性
        if (propStore) {
          const mk = makeMergeKey(sheetId, m.startRow, m.startColumn, m.endRow, m.endColumn);
          const mp = propStore.mergeProps[mk];
          if (mp && mp.length > 0) merge.props = mp;
        }
        sheet.merges.push(merge);
      }
    }

    // slave 集合（跳过合并区域内的非主单元格）
    const slavePositions = new Set<string>();
    // 合并区域主单元格 → 合并区域映射（用于边框收集）
    const mergeMasterMap = new Map<string, ExcelMerge>();
    for (const m of sheet.merges) {
      mergeMasterMap.set(`${m.startRow},${m.startCol}`, m);
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

          // 查找单元格属性
          let cellProps: CellProp[] | undefined;
          if (propStore) {
            const ck = makeCellKey(sheetId, row, col);
            const cp = propStore.cellProps[ck];
            if (cp && cp.length > 0) cellProps = cp;
          }

          // 跳过完全空的单元格（无值、无样式、无属性）
          if (value == null && !formula && !richText && !style && !cellProps) continue;

          const cell: ExcelCell = {
            row,
            col,
            ref: rowColToA1(row, col),
            value: value ?? null,
          };
          if (formula) cell.formula = formula;
          if (richText) cell.richText = richText;
          if (style) cell.style = style;
          if (cellProps) cell.props = cellProps;

          // 合并单元格边框收集：从从属单元格收集右边框和下边框
          const mergeInfo = mergeMasterMap.get(`${row},${col}`);
          if (mergeInfo && cellData) {
            const mergeBorders: ExcelBorders = {};
            let hasMergeBorders = false;

            // 保留主单元格已有的边框
            if (cell.style?.borders) {
              Object.assign(mergeBorders, cell.style.borders);
              hasMergeBorders = true;
            }

            // 收集右边框：遍历合并区域最右列的每个单元格
            const rightCol = mergeInfo.startCol + mergeInfo.colSpan - 1;
            if (!mergeBorders.right) {
              for (let r = mergeInfo.startRow; r < mergeInfo.startRow + mergeInfo.rowSpan; r++) {
                const rightRaw = cellData[String(r)]?.[String(rightCol)];
                if (!rightRaw) continue;
                let rightStyle: Record<string, unknown> | undefined;
                const rs = rightRaw.s;
                if (rs) {
                  if (typeof rs === 'string') rightStyle = globalStyles?.[rs];
                  else if (typeof rs === 'object') rightStyle = rs as Record<string, unknown>;
                }
                if (rightStyle) {
                  const rb = parseBorder((rightStyle.bd as Record<string, unknown>)?.r);
                  if (rb) { mergeBorders.right = rb; hasMergeBorders = true; break; }
                }
              }
            }

            // 收集下边框：遍历合并区域最底行的每个单元格
            const bottomRow = mergeInfo.startRow + mergeInfo.rowSpan - 1;
            if (!mergeBorders.bottom) {
              const bottomRowData = cellData[String(bottomRow)];
              if (bottomRowData) {
                for (let c = mergeInfo.startCol; c < mergeInfo.startCol + mergeInfo.colSpan; c++) {
                  const bottomRaw = bottomRowData[String(c)];
                  if (!bottomRaw) continue;
                  let bottomStyle: Record<string, unknown> | undefined;
                  const bs = bottomRaw.s;
                  if (bs) {
                    if (typeof bs === 'string') bottomStyle = globalStyles?.[bs];
                    else if (typeof bs === 'object') bottomStyle = bs as Record<string, unknown>;
                  }
                  if (bottomStyle) {
                    const bb = parseBorder((bottomStyle.bd as Record<string, unknown>)?.b);
                    if (bb) { mergeBorders.bottom = bb; hasMergeBorders = true; break; }
                  }
                }
              }
            }

            if (hasMergeBorders) {
              if (!cell.style) cell.style = {};
              cell.style.borders = mergeBorders;
            }
          }

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

    // 循环块
    if (loopBlocks) {
      const sheetLoopBlocks = loopBlocks.filter((b) => b.sheetId === sheetId);
      if (sheetLoopBlocks.length > 0) {
        sheet.loopBlocks = sheetLoopBlocks.map((b) => {
          const lb: ExcelLoopBlock = {
            id: b.id,
            label: b.label,
            loopVariable: b.loopVariable,
            startRow: b.startRow,
            startCol: b.startColumn,
            endRow: b.endRow,
            endCol: b.endColumn,
          };
          if (propStore) {
            const lp = propStore.loopBlockProps[b.id];
            if (lp && lp.length > 0) lb.props = lp;
          }
          return lb;
        });
      }
    }

    workbook.sheets.push(sheet);
  }

  // 完整 JSON 输出
  console.log(JSON.stringify(workbook, null, 2));
  console.groupEnd();
}

// ─── 边框线型反向映射（可读名称 → Univer 枚举值） ──────────

const BORDER_STYLE_REVERSE: Record<string, number> = {
  thin: 1, hair: 2, dotted: 3, dashed: 4,
  dashDot: 5, dashDotDot: 6, double: 7, medium: 8,
  mediumDashed: 9, mediumDashDot: 10, mediumDashDotDot: 11,
  slantDashDot: 12, thick: 13,
};

// ─── 默认快照数据（演示所有能力） ──────────────────────────

export const MOCK_SNAPSHOT = {
  sheets: [
    // Sheet 1: 主报表
    {
      id: 'sheet-1',
      name: '用户报表',
      rowCount: 15,
      columnCount: 6,
      defaultRowHeight: 24,
      defaultColumnWidth: 88,
      rows: [{ index: 0, height: 36, hidden: false }],
      columns: [{ index: 0, width: 120, hidden: false }],
      merges: [
        { startRow: 0, startCol: 0, rowSpan: 1, colSpan: 3, props: [{ kind: 'field', field: 'sys_user.username', data: { display: 'label' } }] },
      ],
      cells: [
        { row: 0, col: 0, ref: 'A1', value: '用户报表', style: { font: { size: 16, bold: true }, align: 'center' as const, valign: 'middle' as const } },
        { row: 2, col: 0, ref: 'A3', value: '姓名', style: { font: { bold: true }, fill: '#f0f5ff' } },
        { row: 2, col: 1, ref: 'B3', value: '邮箱', style: { font: { bold: true }, fill: '#f0f5ff' } },
        { row: 3, col: 0, ref: 'A4', value: '张三', props: [{ kind: 'field', field: 'sys_user.username', data: { display: 'value' } }] },
        { row: 3, col: 1, ref: 'B4', value: 'zhangsan@test.com', props: [{ kind: 'field', field: 'sys_user.email', data: { display: 'value' } }] },
        { row: 4, col: 0, ref: 'A5', value: 100, style: { font: { color: '#52c41a' }, align: 'right' as const } },
      ],
      loopBlocks: [
        { id: 'loop-1', label: '用户列表', loopVariable: 'sys_user.id', startRow: 2, startCol: 0, endRow: 4, endCol: 1,
          props: [{ kind: 'dataConfig', data: { pageSize: '10' } }] },
      ],
    },
    // Sheet 2: 汇总表
    {
      id: 'sheet-2',
      name: '汇总',
      rowCount: 10,
      columnCount: 4,
      defaultRowHeight: 24,
      defaultColumnWidth: 88,
      cells: [
        { row: 0, col: 0, ref: 'A1', value: '汇总统计', style: { font: { size: 14, bold: true } } },
        { row: 2, col: 0, ref: 'A3', value: '总金额', style: { font: { bold: true } } },
        { row: 2, col: 1, ref: 'B3', value: 5000, props: [{ kind: 'aggregation', data: { method: 'sum' } }] },
      ],
      loopBlocks: [],
    },
  ],
};

// ─── 渲染快照 ────────────────────────────────────────────

/**
 * 将快照数据渲染到 Univer 工作表中
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderWorkbookSnapshot(
  api: any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  snapshot: any,
  onRendered?: (props: {
    cellProps: Record<string, CellProp[]>;
    mergeProps: Record<string, CellProp[]>;
    loopBlockProps: Record<string, CellProp[]>;
    loopBlocks: LoopBlock[];
  }) => void,
): void {
  const workbook = api?.getActiveWorkbook?.();
  if (!workbook) {
    console.warn('⚠️ 未找到活动工作簿');
    return;
  }

  const sheetsData = snapshot.sheets;
  if (!sheetsData || sheetsData.length === 0) {
    console.warn('⚠️ 快照中无工作表数据');
    return;
  }

  // 收集属性（跨所有 sheet）
  const cellProps: Record<string, CellProp[]> = {};
  const mergeProps: Record<string, CellProp[]> = {};
  const loopBlockProps: Record<string, CellProp[]> = {};
  const loopBlocks: LoopBlock[] = [];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const renderSheet = (sheet: any, sheetData: any) => {
    // 设置工作表名称
    if (sheetData.name) sheet.setName(sheetData.name);

    // 设置尺寸
    if (sheetData.columnCount) sheet.setColumnCount(sheetData.columnCount);
    if (sheetData.rowCount) sheet.setRowCount(sheetData.rowCount);

    // 设置行高
    const rows: Array<{ index: number; height: number }> = sheetData.rows || [];
    for (const r of rows) {
      sheet.setRowHeight(r.index, r.height);
    }

    // 设置列宽
    const columns: Array<{ index: number; width: number }> = sheetData.columns || [];
    for (const c of columns) {
      sheet.setColumnWidth(c.index, c.width);
    }

    const sheetId = sheet.getSheetId?.() ?? 'unknown';

    // 渲染单元格
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const cells: Array<any> = sheetData.cells || [];
    for (const cell of cells) {
      const range = sheet.getRange(cell.row, cell.col);
      if (!range) continue;

      if (cell.value != null) {
        range.setValue(cell.value);
      }

      const s = cell.style;
      if (s) {
        if (s.font?.color) range.setFontColor(s.font.color);
        if (s.font?.size) range.setFontSize(s.font.size);
        if (s.font?.bold) range.setFontWeight('bold');
        if (s.fill) range.setBackground(s.fill);
        if (s.align) {
          const hAlignMap: Record<string, string> = { right: 'normal', justify: 'left', distributed: 'left' };
          range.setHorizontalAlignment(hAlignMap[s.align] || s.align);
        }
        if (s.valign) range.setVerticalAlignment(s.valign);

        if (s.borders) {
          const Enum = api.Enum;
          const sides = ['top', 'bottom', 'left', 'right'] as const;
          for (const side of sides) {
            const border = s.borders[side];
            if (border) {
              const styleVal = BORDER_STYLE_REVERSE[border.style] ?? 1;
              range.setBorder(Enum.BorderType[side.toUpperCase()], styleVal, border.color || '#000000');
            }
          }
        }
      }

      if (cell.props && cell.props.length > 0) {
        cellProps[`${sheetId}:${cell.row}:${cell.col}`] = cell.props;
      }
    }

    // 渲染合并区域
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const merges: Array<any> = sheetData.merges || [];
    for (const m of merges) {
      const range = sheet.getRange(m.startRow, m.startCol, m.rowSpan, m.colSpan);
      if (range) range.merge();
      if (m.props && m.props.length > 0) {
        const mk = `merge:${sheetId}:${m.startRow}:${m.startCol}:${m.startRow + m.rowSpan - 1}:${m.startCol + m.colSpan - 1}`;
        mergeProps[mk] = m.props;
      }
    }

    // 收集循环块
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const rawLoopBlocks: Array<any> = sheetData.loopBlocks || [];
    for (const lb of rawLoopBlocks) {
      loopBlocks.push({
        id: lb.id,
        sheetId,
        startRow: lb.startRow,
        startColumn: lb.startCol,
        endRow: lb.endRow,
        endColumn: lb.endCol,
        label: lb.label,
        loopVariable: lb.loopVariable,
      });
      if (lb.props && lb.props.length > 0) {
        loopBlockProps[lb.id] = lb.props;
      }
    }
  };

  // 遍历所有 sheet 数据
  for (let i = 0; i < sheetsData.length; i++) {
    const sheetData = sheetsData[i];
    if (i === 0) {
      // 第一个 sheet 使用已有的活动工作表
      const sheet = workbook.getActiveSheet();
      if (sheet) renderSheet(sheet, sheetData);
    } else {
      // 后续 sheet 创建新工作表
      const newSheet = workbook.insertSheet(sheetData.name || `Sheet${i + 1}`);
      if (newSheet) renderSheet(newSheet, sheetData);
    }
  }

  // 切回第一个 sheet
  const firstSheet = workbook.getSheets?.()?.[0];
  if (firstSheet) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (firstSheet as any).activate?.();
  }

  console.log(`✅ [渲染快照] 完成: ${sheetsData.length} 个工作表`, {
    loopBlocks: loopBlocks.length,
    cellProps: Object.keys(cellProps).length,
    mergeProps: Object.keys(mergeProps).length,
    loopBlockProps: Object.keys(loopBlockProps).length,
  });

  onRendered?.({ cellProps, mergeProps, loopBlockProps, loopBlocks });
}
