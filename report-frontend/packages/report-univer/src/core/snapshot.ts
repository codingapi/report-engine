import type { WorkbookSnapshot, SnapshotSheet, SnapshotCell } from '../types';

// FWorkbook 类型仅在内部使用，不对外暴露
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type FWorkbook = any;

/** 行列索引 → A1 表示法 */
function rowColToA1(row: number, col: number): string {
    let colStr = '';
    let c = col;
    while (c >= 0) {
        colStr = String.fromCharCode(65 + (c % 26)) + colStr;
        c = Math.floor(c / 26) - 1;
    }
    return `${colStr}${row + 1}`;
}

/** A1 表示法 → [row, col] */
function a1ToRowCol(a1: string): [number, number] {
    const match = a1.match(/^([A-Z]+)(\d+)$/);
    if (!match) return [0, 0];
    let col = 0;
    for (const ch of match[1]) {
        col = col * 26 + (ch.charCodeAt(0) - 64);
    }
    return [parseInt(match[2]) - 1, col - 1];
}

/** 样式字段名称映射 */
const STYLE_FIELD_NAMES: Record<string, string> = {
    ff: 'fontFamily',
    fs: 'fontSize',
    bl: 'bold',
    it: 'italic',
    ul: 'underline',
    st: 'strikethrough',
    bg: 'background',
    cl: 'foreground',
    bd: 'border',
    n: 'numberFormat',
    ht: 'horizontalAlign',
    vt: 'verticalAlign',
    tb: 'wrapStrategy',
    tr: 'textRotation',
    pd: 'padding',
};

/** 解析富文本 IDocumentData */
function parseRichText(docData: Record<string, unknown>): {
    text: string;
    textRuns: Array<{ text: string; range: string; style: Record<string, unknown> | null }> | null;
} {
    const body = docData.body as Record<string, unknown> | undefined;
    const dataStream = (body?.dataStream as string) || (docData.dataStream as string) || '';
    const cleanText = dataStream.replace(/[\r\n]+$/, '');

    let textRuns: Array<{ text: string; range: string; style: Record<string, unknown> | null }> | null = null;

    if (body) {
        const rawRuns = body.textRuns as Array<Record<string, unknown>> | undefined;
        if (rawRuns && rawRuns.length > 0) {
            textRuns = rawRuns.map(run => {
                const start = run.st as number;
                const end = run.ed as number;
                const ts = run.ts as Record<string, unknown> | undefined;
                return {
                    text: cleanText.slice(start, end),
                    range: `${start}-${end}`,
                    style: ts ? parseStyleData(ts) : null,
                };
            });
        }
    }

    return { text: cleanText, textRuns };
}

/** 解析样式对象，缩写键名 → 可读字段名 */
function parseStyleData(style: Record<string, unknown> | undefined): Record<string, unknown> | null {
    if (!style || typeof style !== 'object') return null;
    const result: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(style)) {
        const readableKey = STYLE_FIELD_NAMES[key] || key;
        result[readableKey] = value;
    }
    return Object.keys(result).length > 0 ? result : null;
}

/**
 * 从 FWorkbook 提取完整工作簿快照
 * 返回结构化的 WorkbookSnapshot 数据
 */
export function extractSnapshot(fWorkbook: FWorkbook): WorkbookSnapshot {
    const snapshot = fWorkbook.save();

    // 解析全局样式表
    const stylesMap = (snapshot as Record<string, unknown>).styles as Record<string, Record<string, unknown>> | undefined;
    const styles: Record<string, Record<string, unknown>> = {};
    if (stylesMap && typeof stylesMap === 'object') {
        for (const [styleId, styleData] of Object.entries(stylesMap)) {
            const parsed = parseStyleData(styleData);
            if (parsed) styles[styleId] = parsed;
        }
    }

    // 遍历所有工作表
    const rawSheets = (snapshot as Record<string, unknown>).sheets as Record<string, Record<string, unknown>> | undefined;
    const sheets: SnapshotSheet[] = [];

    if (!rawSheets) return { styles, sheets };

    for (const [sheetId, sheetData] of Object.entries(rawSheets)) {
        const sheetName = (sheetData.name as string) || sheetId;

        // 合并单元格信息
        const rawMergeData = sheetData.mergeData as Array<Record<string, number>> | undefined;
        const mergeData = (rawMergeData || []).map(m => ({
            startRow: m.startRow,
            startColumn: m.startColumn,
            endRow: m.endRow,
            endColumn: m.endColumn,
        }));

        // 构建 slave 查找表（跳过合并区域内的非 master 单元格）
        const slavePositions = new Set<string>();
        for (const m of mergeData) {
            for (let r = m.startRow; r <= m.endRow; r++) {
                for (let c = m.startColumn; c <= m.endColumn; c++) {
                    if (r !== m.startRow || c !== m.startColumn) {
                        slavePositions.add(`${r},${c}`);
                    }
                }
            }
        }

        // 构建 master 合并信息查找表
        const mergeLookup = new Map<string, SnapshotCell['merge']>();
        for (const m of mergeData) {
            const rangeLabel = `${rowColToA1(m.startRow, m.startColumn)}:${rowColToA1(m.endRow, m.endColumn)}`;
            mergeLookup.set(`${m.startRow},${m.startColumn}`, {
                mergeRange: rangeLabel,
                startRow: m.startRow,
                startColumn: m.startColumn,
                endRow: m.endRow,
                endColumn: m.endColumn,
            });
        }

        // 单元格数据
        const cellData = sheetData.cellData as Record<string, Record<string, Record<string, unknown>>> | undefined;
        const cells: SnapshotCell[] = [];

        if (cellData) {
            for (const [rowStr, rowData] of Object.entries(cellData)) {
                const row = parseInt(rowStr);
                for (const [colStr, cell] of Object.entries(rowData)) {
                    const col = parseInt(colStr);
                    const key = `${row},${col}`;

                    if (slavePositions.has(key)) continue;

                    // 解析样式
                    const rawStyle = cell.s;
                    let styleData: Record<string, unknown> | null = null;
                    if (rawStyle) {
                        if (typeof rawStyle === 'string') {
                            styleData = stylesMap ? parseStyleData(stylesMap[rawStyle]) : null;
                        } else if (typeof rawStyle === 'object') {
                            styleData = parseStyleData(rawStyle as Record<string, unknown>);
                        }
                    }

                    // 解析富文本
                    let richText: SnapshotCell['richText'] | undefined;
                    let richTextValue: string | null = null;
                    if (cell.p && typeof cell.p === 'object') {
                        const parsed = parseRichText(cell.p as Record<string, unknown>);
                        richTextValue = parsed.text || null;
                        if (parsed.textRuns) {
                            richText = { text: parsed.text, textRuns: parsed.textRuns };
                        }
                    }

                    const value = (cell.v != null && cell.v !== '') ? cell.v : richTextValue;
                    if (value == null && !cell.f && !richText) continue;

                    const mergeInfo = mergeLookup.get(key) || undefined;

                    const snapshotCell: SnapshotCell = {
                        cell: rowColToA1(row, col),
                        value: value ?? null,
                    };
                    if (richText) snapshotCell.richText = richText;
                    if (cell.f) snapshotCell.formula = cell.f as string;
                    if (styleData) snapshotCell.style = styleData;
                    if (mergeInfo) snapshotCell.merge = mergeInfo;

                    cells.push(snapshotCell);
                }
            }
        }

        // 按行列排序
        cells.sort((a, b) => {
            const [ar, ac] = a1ToRowCol(a.cell);
            const [br, bc] = a1ToRowCol(b.cell);
            return ar - br || ac - bc;
        });

        const sheet: SnapshotSheet = {
            sheetId,
            name: sheetName,
            cells,
            mergeData,
        };

        const rowData = sheetData.rowData as Record<string, unknown> | undefined;
        const columnData = sheetData.columnData as Record<string, unknown> | undefined;
        if (rowData) sheet.rowData = rowData;
        if (columnData) sheet.columnData = columnData;

        sheets.push(sheet);
    }

    return { styles, sheets };
}
