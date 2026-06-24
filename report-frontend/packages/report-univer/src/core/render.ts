/**
 * 快照渲染
 * 将 ExcelWorkbook 数据渲染到 Univer 工作表中（导入）
 * 与 extractSnapshot（导出）使用同一数据结构
 */

import type { ExcelWorkbook, ExcelBorderStyle, LoopBlockConfig, RenderResult } from '@/types';
import type { UniverAPI } from './setup';

/** 可读边框线型 → Univer 枚举值 */
const BORDER_STYLE_REVERSE: Record<ExcelBorderStyle, number> = {
  thin: 1,
  hair: 2,
  dotted: 3,
  dashed: 4,
  dashDot: 5,
  dashDotDot: 6,
  double: 7,
  medium: 8,
  mediumDashed: 9,
  mediumDashDot: 10,
  mediumDashDotDot: 11,
  slantDashDot: 12,
  thick: 13,
};

/** 水平对齐 → Univer 值 */
const H_ALIGN_REVERSE: Record<string, string> = {
  left: 'left',
  center: 'center',
  right: 'right',
  justify: 'left',
  distributed: 'left',
};

/**
 * 将快照数据渲染到 Univer 工作表中
 * @returns 从快照中还原的属性和循环块数据
 */
export function renderSnapshot<TCellProp = unknown, TLoopProp = unknown>(
  univerAPI: UniverAPI,
  snapshot: ExcelWorkbook<TCellProp, TLoopProp>,
): RenderResult<TCellProp, TLoopProp> {
  const cellProps: Record<string, TCellProp[]> = {};
  const mergeProps: Record<string, TCellProp[]> = {};
  const loopBlockProps: Record<string, TLoopProp[]> = {};
  const loopBlocks: LoopBlockConfig[] = [];

  const workbook = univerAPI.getActiveWorkbook();
  if (!workbook) return { cellProps, mergeProps, loopBlockProps, loopBlocks };

  const sheetsData = snapshot.sheets;
  if (!sheetsData || sheetsData.length === 0) {
    return { cellProps, mergeProps, loopBlockProps, loopBlocks };
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const renderSheet = (sheet: any, sheetData: any) => {
    if (sheetData.name) sheet.setName(sheetData.name);
    if (sheetData.columnCount) sheet.setColumnCount(sheetData.columnCount);
    if (sheetData.rowCount) sheet.setRowCount(sheetData.rowCount);

    // 设置行高
    const rows = sheetData.rows || [];
    for (const r of rows) {
      if (r.height != null) sheet.setRowHeight(r.index, r.height);
    }

    // 设置列宽
    const columns = sheetData.columns || [];
    for (const c of columns) {
      if (c.width != null) sheet.setColumnWidth(c.index, c.width);
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
        if (s.font?.family) range.setFontFamily(s.font.family);
        if (s.font?.color) range.setFontColor(s.font.color);
        if (s.font?.size) range.setFontSize(s.font.size);
        if (s.font?.bold) range.setFontWeight('bold');
        if (s.font?.italic) range.setFontStyle('italic');
        if (s.font?.underline) range.setFontLine('underline');
        else if (s.font?.strikethrough) range.setFontLine('line-through');
        if (s.fill) range.setBackground(s.fill);
        if (s.align) {
          range.setHorizontalAlignment(H_ALIGN_REVERSE[s.align] || s.align);
        }
        if (s.valign) range.setVerticalAlignment(s.valign);

        if (s.borders) {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const Enum = (univerAPI as any).Enum;
          const sides = ['top', 'bottom', 'left', 'right'] as const;
          for (const side of sides) {
            const border = s.borders[side];
            if (border) {
              const styleVal = BORDER_STYLE_REVERSE[border.style as ExcelBorderStyle] ?? 1;
              range.setBorder(
                Enum.BorderType[side.toUpperCase()],
                styleVal,
                border.color || '#000000',
              );
            }
          }
        }
      }

      // 收集单元格属性
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
        const er = m.startRow + m.rowSpan - 1;
        const ec = m.startCol + m.colSpan - 1;
        mergeProps[`merge:${sheetId}:${m.startRow}:${m.startCol}:${er}:${ec}`] = m.props;
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
      const sheet = workbook.getActiveSheet();
      if (sheet) renderSheet(sheet, sheetData);
    } else {
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

  return { cellProps, mergeProps, loopBlockProps, loopBlocks };
}
