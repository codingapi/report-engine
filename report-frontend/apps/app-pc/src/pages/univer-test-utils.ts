/**
 * Univer 数据提取工具函数
 * 用于技术验证：获取工作表的完整快照数据，为后端导出 Excel 提供数据结构参考
 */

import type { FWorkbook } from '@univerjs/core/facade';

/**
 * 行列索引 → A1 表示法（如 0,0 → "A1"）
 */
function rowColToA1(row: number, col: number): string {
  let colStr = '';
  let c = col;
  while (c >= 0) {
    colStr = String.fromCharCode(65 + (c % 26)) + colStr;
    c = Math.floor(c / 26) - 1;
  }
  return `${colStr}${row + 1}`;
}

/**
 * A1 表示法 → [row, col]（如 "A1" → [0, 0]）
 */
function a1ToRowCol(a1: string): [number, number] {
  const match = a1.match(/^([A-Z]+)(\d+)$/);
  if (!match) return [0, 0];
  let col = 0;
  for (const ch of match[1]) {
    col = col * 26 + (ch.charCodeAt(0) - 64);
  }
  return [parseInt(match[2]) - 1, col - 1];
}

/**
 * 样式字段名称映射（用于日志可读性）
 */
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

/**
 * 解析富文本 IDocumentData，返回纯文本和分段样式
 */
function parseRichText(docData: Record<string, unknown>): { text: string; textRuns: Array<Record<string, unknown>> | null } {
  const body = docData.body as Record<string, unknown> | undefined;
  const dataStream = (body?.dataStream as string) || (docData.dataStream as string) || '';
  const cleanText = dataStream.replace(/[\r\n]+$/, '');

  let textRuns: Array<Record<string, unknown>> | null = null;

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

/**
 * 解析样式对象，将缩写键名转换为可读字段名
 */
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
 * 提取工作表完整快照数据
 * 通过 fWorkbook.save() 获取 IWorkbookData，解析单元格、样式、合并信息
 */
export function extractWorkbookSnapshot(fWorkbook: FWorkbook): void {
  console.group('📊 [Univer 快照] 工作表完整数据');

  // 1. 获取原始快照
  const snapshot = fWorkbook.save();
  console.log('原始 IWorkbookData:', JSON.parse(JSON.stringify(snapshot)));

  // 2. 解析全局样式表
  const stylesMap = (snapshot as Record<string, unknown>).styles as Record<string, Record<string, unknown>> | undefined;
  if (stylesMap && typeof stylesMap === 'object') {
    console.group('🎨 全局样式表');
    for (const [styleId, styleData] of Object.entries(stylesMap)) {
      const parsed = parseStyleData(styleData);
      if (parsed) {
        console.log(`样式 [${styleId}]:`, parsed);
      }
    }
    console.groupEnd();
  }

  // 3. 遍历所有工作表
  const sheets = (snapshot as Record<string, unknown>).sheets as Record<string, Record<string, unknown>> | undefined;
  if (!sheets) {
    console.log('⚠️ 未找到 sheets 数据');
    console.groupEnd();
    return;
  }

  for (const [sheetId, sheetData] of Object.entries(sheets)) {
    const sheetName = (sheetData.name as string) || sheetId;
    console.group(`📄 工作表: ${sheetName} (${sheetId})`);

    // 3a. 合并单元格信息
    const mergeData = sheetData.mergeData as Array<Record<string, number>> | undefined;
    if (mergeData && mergeData.length > 0) {
      console.log('合并单元格:', mergeData.map(m => ({
        startRow: m.startRow,
        startColumn: m.startColumn,
        endRow: m.endRow,
        endColumn: m.endColumn,
        range: `${rowColToA1(m.startRow, m.startColumn)}:${rowColToA1(m.endRow, m.endColumn)}`,
      })));
    } else {
      console.log('合并单元格: 无');
    }

    // 3b. 构建合并单元格 slave 查找表（用于跳过 slave 单元格输出）
    const slavePositions = new Set<string>();
    if (mergeData && mergeData.length > 0) {
      for (const m of mergeData) {
        for (let r = m.startRow; r <= m.endRow; r++) {
          for (let c = m.startColumn; c <= m.endColumn; c++) {
            if (r !== m.startRow || c !== m.startColumn) {
              slavePositions.add(`${r},${c}`);
            }
          }
        }
      }
    }

    // 3c. 构建 master 合并信息查找表
    const mergeLookup = new Map<string, Record<string, unknown>>();
    if (mergeData && mergeData.length > 0) {
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
    }

    // 3d. 单元格数据
    const cellData = sheetData.cellData as Record<string, Record<string, Record<string, unknown>>> | undefined;
    const cells: Array<Record<string, unknown>> = [];

    if (cellData) {
      for (const [rowStr, rowData] of Object.entries(cellData)) {
        const row = parseInt(rowStr);
        for (const [colStr, cell] of Object.entries(rowData)) {
          const col = parseInt(colStr);
          const key = `${row},${col}`;

          // 跳过合并区域的 slave 单元格（值在 master 中）
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
          let richText: Record<string, unknown> | null = null;
          let richTextValue: string | null = null;
          if (cell.p && typeof cell.p === 'object') {
            const parsed = parseRichText(cell.p as Record<string, unknown>);
            richTextValue = parsed.text || null;
            // 只在有分段样式时输出 richText
            if (parsed.textRuns) {
              richText = { text: parsed.text, textRuns: parsed.textRuns };
            }
          }

          // value 始终有值：优先 v，其次富文本纯文本
          const value = (cell.v != null && cell.v !== '') ? cell.v : richTextValue;

          // 跳过完全空的单元格
          if (value == null && !cell.f && !richText) continue;

          // 合并信息（仅 master）
          const mergeInfo = mergeLookup.get(key) || null;

          cells.push({
            cell: rowColToA1(row, col),
            value: value ?? null,
            ...(richText ? { richText } : {}),
            ...(cell.f ? { formula: cell.f } : {}),
            ...(styleData ? { style: styleData } : {}),
            ...(mergeInfo ? { merge: mergeInfo } : {}),
          });
        }
      }
    }

    // 按行列排序
    cells.sort((a, b) => {
      const [ar, ac] = a1ToRowCol(a.cell as string);
      const [br, bc] = a1ToRowCol(b.cell as string);
      return ar - br || ac - bc;
    });

    if (cells.length > 0) {
      console.log(`单元格数据 (${cells.length} 个):`, cells);
    } else {
      console.log('单元格数据: 空');
    }

    // 3e. 行列配置
    const rowData = sheetData.rowData as Record<string, unknown> | undefined;
    const colData = sheetData.columnData as Record<string, unknown> | undefined;
    if (rowData) console.log('行配置:', rowData);
    if (colData) console.log('列配置:', colData);

    console.groupEnd();
  }

  console.groupEnd();
}

