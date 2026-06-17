/**
 * 工作簿快照提取
 * 输出后端友好的 Excel 数据结构，可直接映射到 Apache POI API
 * 导入导出使用同一数据结构
 */

import type {
    ExcelWorkbook, ExcelSheet, ExcelCell, ExcelStyle, ExcelFont,
    ExcelBorders, ExcelBorder, ExcelBorderStyle,
    ExcelRichText, ExcelMerge,
    ExcelLoopBlock, LoopBlockConfig,
} from '@/types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type FWorkbook = any;

/** extractSnapshot 可选参数 */
export interface ExtractOptions<TCellProp = unknown, TLoopProp = unknown> {
    /** 单元格属性存储: key = `${sheetId}:${row}:${col}` */
    cellProps?: Record<string, TCellProp[]>;
    /** 合并区域属性存储: key = `merge:${sheetId}:${sr}:${sc}:${er}:${ec}` */
    mergeProps?: Record<string, TCellProp[]>;
    /** 循环块配置列表 */
    loopBlocks?: LoopBlockConfig[];
    /** 循环块属性存储: key = blockId */
    loopBlockProps?: Record<string, TLoopProp[]>;
}

// ─── Key 生成 ─────────────────────────────────────────────────

const makeCellKey = (sheetId: string, row: number, col: number): string =>
    `${sheetId}:${row}:${col}`;

const makeMergeKey = (sheetId: string, sr: number, sc: number, er: number, ec: number): string =>
    `merge:${sheetId}:${sr}:${sc}:${er}:${ec}`;

// ─── 枚举映射 ────────────────────────────────────────────────

const H_ALIGN: Record<number, ExcelStyle['align']> = {
    1: 'left', 2: 'center', 3: 'right', 4: 'justify', 6: 'distributed',
};

const V_ALIGN: Record<number, ExcelStyle['valign']> = {
    1: 'top', 2: 'middle', 3: 'bottom',
};

const WRAP: Record<number, boolean> = {
    0: false, 1: false, 2: false, 3: true,
};

const BORDER_STYLE: Record<number, ExcelBorderStyle> = {
    1: 'thin', 2: 'hair', 3: 'dotted', 4: 'dashed',
    5: 'dashDot', 6: 'dashDotDot', 7: 'double', 8: 'medium',
    9: 'mediumDashed', 10: 'mediumDashDot', 11: 'mediumDashDotDot',
    12: 'slantDashDot', 13: 'thick',
};

// ─── 工具函数 ────────────────────────────────────────────────

function rowColToA1(row: number, col: number): string {
    let colStr = '';
    let c = col;
    while (c >= 0) {
        colStr = String.fromCharCode(65 + (c % 26)) + colStr;
        c = Math.floor(c / 26) - 1;
    }
    return `${colStr}${row + 1}`;
}

// ─── 解析函数 ────────────────────────────────────────────────

function parseColor(cl: unknown): string | undefined {
    if (!cl || typeof cl !== 'object') return undefined;
    return (cl as Record<string, unknown>).rgb as string | undefined;
}

function parseFont(raw: Record<string, unknown>): ExcelFont | undefined {
    const font: ExcelFont = {};
    let has = false;
    if (raw.ff) { font.family = raw.ff as string; has = true; }
    if (raw.fs) { font.size = raw.fs as number; has = true; }
    if (raw.bl === 1) { font.bold = true; has = true; }
    if (raw.it === 1) { font.italic = true; has = true; }
    if (raw.ul && typeof raw.ul === 'object' && (raw.ul as Record<string, unknown>).s === 1) {
        font.underline = true; has = true;
    }
    if (raw.st && typeof raw.st === 'object' && (raw.st as Record<string, unknown>).s === 1) {
        font.strikethrough = true; has = true;
    }
    const cl = parseColor(raw.cl);
    if (cl) { font.color = cl; has = true; }
    return has ? font : undefined;
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
    let has = false;
    const t = parseBorder(raw.t); if (t) { borders.top = t; has = true; }
    const r = parseBorder(raw.r); if (r) { borders.right = r; has = true; }
    const b = parseBorder(raw.b); if (b) { borders.bottom = b; has = true; }
    const l = parseBorder(raw.l); if (l) { borders.left = l; has = true; }
    return has ? borders : undefined;
}

function parsePadding(pd: unknown): ExcelStyle['padding'] | undefined {
    if (!pd || typeof pd !== 'object') return undefined;
    const raw = pd as Record<string, unknown>;
    const result: NonNullable<ExcelStyle['padding']> = {};
    let has = false;
    if (raw.t != null) { result.top = raw.t as number; has = true; }
    if (raw.r != null) { result.right = raw.r as number; has = true; }
    if (raw.b != null) { result.bottom = raw.b as number; has = true; }
    if (raw.l != null) { result.left = raw.l as number; has = true; }
    return has ? result : undefined;
}

function parseStyle(raw: Record<string, unknown>): ExcelStyle | undefined {
    const style: ExcelStyle = {};
    let has = false;

    const font = parseFont(raw);
    if (font) { style.font = font; has = true; }

    if (raw.ht != null) {
        const align = H_ALIGN[raw.ht as number];
        if (align) { style.align = align; has = true; }
    }
    if (raw.vt != null) {
        const valign = V_ALIGN[raw.vt as number];
        if (valign) { style.valign = valign; has = true; }
    }
    if (raw.tb != null && WRAP[raw.tb as number]) {
        style.wrap = true; has = true;
    }
    if (raw.tr && typeof raw.tr === 'object') {
        const a = (raw.tr as Record<string, unknown>).a as number;
        if (a) { style.rotation = a; has = true; }
    }

    const bg = parseColor(raw.bg);
    if (bg) { style.fill = bg; has = true; }

    const borders = parseBorders(raw.bd);
    if (borders) { style.borders = borders; has = true; }

    if (raw.n && typeof raw.n === 'object') {
        const pattern = (raw.n as Record<string, unknown>).pattern as string;
        if (pattern) { style.numberFormat = pattern; has = true; }
    }

    const padding = parsePadding(raw.pd);
    if (padding) { style.padding = padding; has = true; }

    return has ? style : undefined;
}

function parseRichText(docData: Record<string, unknown>): ExcelRichText | undefined {
    const body = docData.body as Record<string, unknown> | undefined;
    const dataStream = (body?.dataStream as string) || (docData.dataStream as string) || '';
    const text = dataStream.replace(/[\r\n]+$/, '');

    if (!body) return undefined;

    const rawRuns = body.textRuns as Array<Record<string, unknown>> | undefined;
    if (!rawRuns || rawRuns.length === 0) return undefined;

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

// ─── 主函数 ──────────────────────────────────────────────────

/**
 * 从 FWorkbook 提取完整工作簿快照（Excel 友好格式）
 * 支持附带属性存储和循环块配置
 */
export function extractSnapshot<TCellProp = unknown, TLoopProp = unknown>(
    fWorkbook: FWorkbook,
    options?: ExtractOptions<TCellProp, TLoopProp>,
): ExcelWorkbook<TCellProp, TLoopProp> {
    const snapshot = fWorkbook.save();
    const rawSheets = (snapshot as Record<string, unknown>).sheets as Record<string, Record<string, unknown>> | undefined;
    const globalStyles = (snapshot as Record<string, unknown>).styles as Record<string, Record<string, unknown>> | undefined;

    if (!rawSheets) return { sheets: [] };

    const sheets: ExcelSheet<TCellProp, TLoopProp>[] = [];

    for (const [sheetId, sheetData] of Object.entries(rawSheets)) {
        const sheet: ExcelSheet<TCellProp, TLoopProp> = {
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
                const merge: ExcelMerge<TCellProp> = {
                    startRow: m.startRow,
                    startCol: m.startColumn,
                    rowSpan: m.endRow - m.startRow + 1,
                    colSpan: m.endColumn - m.startColumn + 1,
                };
                // 查找合并区域属性
                if (options?.mergeProps) {
                    const mk = makeMergeKey(sheetId, m.startRow, m.startColumn, m.endRow, m.endColumn);
                    const mp = options.mergeProps[mk];
                    if (mp && mp.length > 0) merge.props = mp;
                }
                sheet.merges.push(merge);
            }
        }

        // slave 集合
        const slavePositions = new Set<string>();
        // 合并区域主单元格映射（用于边框收集）
        const mergeMasterMap = new Map<string, ExcelMerge<TCellProp>>();
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

                    // 富文本
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
                        ? rawCell.f.slice(1)
                        : undefined;

                    const style = resolvedStyle ? parseStyle(resolvedStyle) : undefined;

                    // 查找单元格属性
                    let cellProps: TCellProp[] | undefined;
                    if (options?.cellProps) {
                        const ck = makeCellKey(sheetId, row, col);
                        const cp = options.cellProps[ck];
                        if (cp && cp.length > 0) cellProps = cp;
                    }

                    // 跳过完全空的单元格（无值、无样式、无属性）
                    if (value == null && !formula && !richText && !style && !cellProps) continue;

                    const cell: ExcelCell<TCellProp> = {
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

                        if (cell.style?.borders) {
                            Object.assign(mergeBorders, cell.style.borders);
                            hasMergeBorders = true;
                        }

                        // 右边框：合并区域最右列
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

                        // 下边框：合并区域最底行
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
        const rawRowData = sheetData.rowData as Record<string, unknown> | undefined;
        if (rawRowData) {
            for (const [idx, r] of Object.entries(rawRowData)) {
                const raw = r as Record<string, unknown>;
                sheet.rows.push({
                    index: parseInt(idx),
                    height: (raw.h as number) ?? sheet.defaultRowHeight,
                    hidden: raw.hd === 1,
                });
            }
        }

        // 列配置
        const rawColData = sheetData.columnData as Record<string, unknown> | undefined;
        if (rawColData) {
            for (const [idx, c] of Object.entries(rawColData)) {
                const raw = c as Record<string, unknown>;
                sheet.columns.push({
                    index: parseInt(idx),
                    width: (raw.w as number) ?? sheet.defaultColumnWidth,
                    hidden: raw.hd === 1,
                });
            }
        }

        // 循环块
        if (options?.loopBlocks) {
            const sheetLoopBlocks = options.loopBlocks.filter((b) => b.sheetId === sheetId);
            if (sheetLoopBlocks.length > 0) {
                sheet.loopBlocks = sheetLoopBlocks.map((b) => {
                    const lb: ExcelLoopBlock<TLoopProp> = {
                        id: b.id,
                        label: b.label,
                        startRow: b.startRow,
                        startCol: b.startColumn,
                        endRow: b.endRow,
                        endCol: b.endColumn,
                    };
                    if (options?.loopBlockProps) {
                        const lp = options.loopBlockProps[b.id];
                        if (lp && lp.length > 0) lb.props = lp;
                    }
                    return lb;
                });
            }
        }

        sheets.push(sheet);
    }

    return { sheets };
}
