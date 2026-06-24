/**
 * 单元格操作句柄
 * 封装 Univer Facade API，提供类型安全的单元格操作
 */

import type { CellHandle, CellStyleSnapshot, ExcelBorderStyle } from '@/types';
import type { UniverAPI } from './setup';

/** 边框方向 → Univer BorderType 枚举名 */
const BORDER_SIDE_MAP: Record<string, string> = {
  top: 'TOP',
  right: 'RIGHT',
  bottom: 'BOTTOM',
  left: 'LEFT',
};

/** 可读边框线型 → Univer 枚举值 */
const BORDER_STYLE_VALUE: Record<ExcelBorderStyle, number> = {
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

/**
 * 创建单元格操作句柄
 */
export function createCellHandle(
  univerAPI: UniverAPI,
  sheetId: string,
  row: number,
  col: number,
  a1Notation: string,
): CellHandle {
  // 获取 Range 的延迟调用（每次操作重新获取，避免过期引用）
  const getRange = () => {
    const workbook = univerAPI.getActiveWorkbook();
    if (!workbook) return null;
    const sheet = workbook.getSheetBySheetId(sheetId);
    if (!sheet) return null;
    return sheet.getRange(row, col);
  };

  return {
    // ─── 信息 ───
    sheetId,
    row,
    col,
    a1Notation,

    // ─── 值操作 ───
    setValue: (value: string | number | boolean) => {
      const range = getRange();
      if (range) range.setValue(value);
    },

    // ─── 样式操作 ───
    setFontColor: (color: string) => {
      const range = getRange();
      if (range) range.setFontColor(color);
    },

    setBackground: (color: string) => {
      const range = getRange();
      if (range) range.setBackground(color);
    },

    setFontSize: (size: number) => {
      const range = getRange();
      if (range) range.setFontSize(size);
    },

    setFontWeight: (weight: 'bold' | 'normal') => {
      const range = getRange();
      if (range) range.setFontWeight(weight);
    },

    setBorder: (
      side: 'top' | 'right' | 'bottom' | 'left',
      style: ExcelBorderStyle,
      color: string,
    ) => {
      const range = getRange();
      if (!range) return;
      const borderType = BORDER_SIDE_MAP[side];
      const styleValue = BORDER_STYLE_VALUE[style] ?? 1;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const Enum = (univerAPI as any).Enum;
      if (Enum?.BorderType?.[borderType] != null) {
        range.setBorder(Enum.BorderType[borderType], styleValue, color);
      }
    },

    clearFormat: () => {
      const range = getRange();
      if (range) range.clearFormat();
    },

    // ─── 样式读取 ───
    getStyle: (): CellStyleSnapshot => {
      const range = getRange();
      if (!range) return {};
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const raw = (range as any).getCellStyleData?.('cell');
      if (!raw) return {};
      const result: CellStyleSnapshot = {};
      if (raw.cl?.rgb) result.fontColor = raw.cl.rgb;
      if (raw.bg?.rgb) result.background = raw.bg.rgb;
      if (raw.fs) result.fontSize = raw.fs;
      if (raw.bl === 1) result.bold = true;
      return result;
    },
  };
}
