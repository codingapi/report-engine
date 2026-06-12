/**
 * Univer 验证页面 — 测试数据
 *
 * extractSnapshot / renderSnapshot 等工具已迁移至 @coding-report/report-univer
 * 此文件仅保留演示用的 MOCK_SNAPSHOT 数据
 */

import type { CellProp, ExcelWorkbook } from '@coding-report/report-univer';

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
        { startRow: 0, startCol: 0, rowSpan: 1, colSpan: 3, props: [{ kind: 'field', field: 'sys_user.username', data: { display: 'label' } }] as CellProp[] },
      ],
      cells: [
        { row: 0, col: 0, ref: 'A1', value: '用户报表', style: { font: { size: 16, bold: true }, align: 'center' as const, valign: 'middle' as const } },
        { row: 2, col: 0, ref: 'A3', value: '姓名', style: { font: { bold: true }, fill: '#f0f5ff' } },
        { row: 2, col: 1, ref: 'B3', value: '邮箱', style: { font: { bold: true }, fill: '#f0f5ff' } },
        { row: 3, col: 0, ref: 'A4', value: '张三', props: [{ kind: 'field', field: 'sys_user.username', data: { display: 'value' } }] as CellProp[] },
        { row: 3, col: 1, ref: 'B4', value: 'zhangsan@test.com', props: [{ kind: 'field', field: 'sys_user.email', data: { display: 'value' } }] as CellProp[] },
        { row: 4, col: 0, ref: 'A5', value: 100, style: { font: { color: '#52c41a' }, align: 'right' as const } },
      ],
      loopBlocks: [
        { id: 'loop-1', label: '用户列表', startRow: 2, startCol: 0, endRow: 4, endCol: 1,
          props: [{ kind: 'dataConfig', data: { pageSize: '10', loopVariable: 'sys_user.id' } }] as CellProp[] },
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
        { row: 2, col: 1, ref: 'B3', value: 5000, props: [{ kind: 'aggregation', data: { method: 'sum' } }] as CellProp[] },
      ],
      loopBlocks: [],
    },
  ],
};

// ─── 样式测试快照（验证 POI 构建能力） ──────────────────

/** 通用细边框 */
const thinBorder = { style: 'thin' as const, color: '#000000' };
const thinBorders = { top: thinBorder, bottom: thinBorder, left: thinBorder, right: thinBorder };

/** 表头样式 */
const headerStyle = (borderOverride?: Record<string, { style: string; color: string }>) => ({
  font: { bold: true, size: 12, color: '#333333' },
  align: 'center' as const,
  valign: 'middle' as const,
  fill: '#D6E4F0',
  borders: borderOverride ?? thinBorders,
});

export const STYLE_TEST_SNAPSHOT: ExcelWorkbook = {
  sheets: [
    {
      id: 'style-sheet-1',
      name: '样式测试',
      rowCount: 20,
      columnCount: 6,
      defaultRowHeight: 24,
      defaultColumnWidth: 88,
      rows: [
        { index: 0, height: 48, hidden: false },
        { index: 1, height: 32, hidden: false },
        { index: 8, height: 60, hidden: false },
      ],
      columns: [
        { index: 0, width: 160, hidden: false },
        { index: 1, width: 120, hidden: false },
        { index: 2, width: 100, hidden: false },
        { index: 3, width: 100, hidden: false },
        { index: 4, width: 140, hidden: false },
        { index: 5, width: 120, hidden: false },
      ],
      merges: [
        { startRow: 0, startCol: 0, rowSpan: 1, colSpan: 6 },
      ],
      cells: [
        // Row 0: 标题（合并 A1:F1）
        { row: 0, col: 0, ref: 'A1', value: '报表样式验证',
          style: { font: { family: 'Microsoft YaHei', size: 18, bold: true, color: '#FFFFFF' }, align: 'center', valign: 'middle', fill: '#1F4E79' } },

        // Row 1: 表头（各种边框风格）
        { row: 1, col: 0, ref: 'A2', value: '字符串', style: headerStyle() },
        { row: 1, col: 1, ref: 'B2', value: '数字', style: headerStyle({ top: { style: 'medium', color: '#1F4E79' }, bottom: { style: 'medium', color: '#1F4E79' }, left: { style: 'medium', color: '#1F4E79' }, right: { style: 'medium', color: '#1F4E79' } }) },
        { row: 1, col: 2, ref: 'C2', value: '布尔值', style: headerStyle({ top: { style: 'thick', color: '#C00000' }, bottom: { style: 'thick', color: '#C00000' }, left: { style: 'thick', color: '#C00000' }, right: { style: 'thick', color: '#C00000' } }) },
        { row: 1, col: 3, ref: 'D2', value: '日期格式', style: headerStyle({ top: { style: 'double', color: '#000000' }, bottom: { style: 'double', color: '#000000' }, left: { style: 'double', color: '#000000' }, right: { style: 'double', color: '#000000' } }) },
        { row: 1, col: 4, ref: 'E2', value: '公式', style: headerStyle({ top: { style: 'dashed', color: '#7030A0' }, bottom: { style: 'dashed', color: '#7030A0' }, left: { style: 'dashed', color: '#7030A0' }, right: { style: 'dashed', color: '#7030A0' } }) },
        { row: 1, col: 5, ref: 'F2', value: '富文本', style: headerStyle({ top: { style: 'dotted', color: '#00B050' }, bottom: { style: 'dotted', color: '#00B050' }, left: { style: 'dotted', color: '#00B050' }, right: { style: 'dotted', color: '#00B050' } }) },

        // Row 2: 数据行
        { row: 2, col: 0, ref: 'A3', value: 'Hello World',
          style: { font: { family: 'Arial', size: 11, italic: true, color: '#1F4E79' }, align: 'left', valign: 'top', borders: thinBorders, padding: { left: 14 } } },
        { row: 2, col: 1, ref: 'B3', value: 3.14159,
          style: { align: 'right', valign: 'middle', numberFormat: '0.00', borders: thinBorders } },
        { row: 2, col: 2, ref: 'C3', value: true,
          style: { align: 'center', valign: 'middle', borders: thinBorders } },
        { row: 2, col: 3, ref: 'D3', value: 45292.5,
          style: { align: 'center', valign: 'middle', numberFormat: 'yyyy-MM-dd', borders: thinBorders } },
        { row: 2, col: 4, ref: 'E3', value: 100, formula: 'SUM(B3:B3)',
          style: { align: 'right', valign: 'middle', numberFormat: '#,##0.00', borders: thinBorders } },
        { row: 2, col: 5, ref: 'F3', value: null,
          richText: { text: '红色粗体+蓝色斜体', segments: [
            { text: '红色粗体', style: { bold: true, color: '#FF0000', size: 12 } },
            { text: '+', style: { size: 12, color: '#333333' } },
            { text: '蓝色斜体', style: { italic: true, color: '#0000FF', size: 12 } },
          ] },
          style: { valign: 'middle', borders: thinBorders } },

        // Row 4: 对齐测试
        { row: 4, col: 0, ref: 'A5', value: '左对齐 + 顶部', style: { align: 'left', valign: 'top', fill: '#FFF2CC' } },
        { row: 4, col: 1, ref: 'B5', value: '居中 + 居中', style: { align: 'center', valign: 'middle', fill: '#FFF2CC' } },
        { row: 4, col: 2, ref: 'C5', value: '右对齐 + 底部', style: { align: 'right', valign: 'bottom', fill: '#FFF2CC' } },

        // Row 5: 换行 + 旋转
        { row: 5, col: 0, ref: 'A6', value: '这是一段很长的文本内容，需要自动换行显示在单元格内部，用于验证 wrap 功能是否正常工作。', style: { wrap: true, valign: 'top' } },
        { row: 5, col: 1, ref: 'B6', value: '旋转45°', style: { rotation: 45, align: 'center' } },
        { row: 5, col: 2, ref: 'C6', value: '旋转90°', style: { rotation: 90, align: 'center' } },

        // Row 7: 字体特性
        { row: 7, col: 0, ref: 'A8', value: 'underline', style: { font: { underline: true, size: 11, color: '#0563C1' } } },
        { row: 7, col: 1, ref: 'B8', value: 'strikethrough', style: { font: { strikethrough: true, size: 11, color: '#999999' } } },
        { row: 7, col: 2, ref: 'C8', value: '粗斜体', style: { font: { bold: true, italic: true, size: 14, family: 'Times New Roman', color: '#7030A0' } } },

        // Row 8-10: 全部 13 种边框线型
        { row: 8, col: 0, ref: 'A9', value: 'thin', style: { borders: { top: { style: 'thin', color: '#000000' }, bottom: { style: 'thin', color: '#000000' }, left: { style: 'thin', color: '#000000' }, right: { style: 'thin', color: '#000000' } } } },
        { row: 8, col: 1, ref: 'B9', value: 'hair', style: { borders: { top: { style: 'hair', color: '#000000' }, bottom: { style: 'hair', color: '#000000' }, left: { style: 'hair', color: '#000000' }, right: { style: 'hair', color: '#000000' } } } },
        { row: 8, col: 2, ref: 'C9', value: 'dotted', style: { borders: { top: { style: 'dotted', color: '#FF0000' }, bottom: { style: 'dotted', color: '#FF0000' }, left: { style: 'dotted', color: '#FF0000' }, right: { style: 'dotted', color: '#FF0000' } } } },
        { row: 8, col: 3, ref: 'D9', value: 'dashed', style: { borders: { top: { style: 'dashed', color: '#00FF00' }, bottom: { style: 'dashed', color: '#00FF00' }, left: { style: 'dashed', color: '#00FF00' }, right: { style: 'dashed', color: '#00FF00' } } } },
        { row: 8, col: 4, ref: 'E9', value: 'dashDot', style: { borders: { top: { style: 'dashDot', color: '#0000FF' }, bottom: { style: 'dashDot', color: '#0000FF' }, left: { style: 'dashDot', color: '#0000FF' }, right: { style: 'dashDot', color: '#0000FF' } } } },
        { row: 8, col: 5, ref: 'F9', value: 'dashDotDot', style: { borders: { top: { style: 'dashDotDot', color: '#FF00FF' }, bottom: { style: 'dashDotDot', color: '#FF00FF' }, left: { style: 'dashDotDot', color: '#FF00FF' }, right: { style: 'dashDotDot', color: '#FF00FF' } } } },

        { row: 9, col: 0, ref: 'A10', value: 'double', style: { borders: { top: { style: 'double', color: '#000000' }, bottom: { style: 'double', color: '#000000' }, left: { style: 'double', color: '#000000' }, right: { style: 'double', color: '#000000' } } } },
        { row: 9, col: 1, ref: 'B10', value: 'medium', style: { borders: { top: { style: 'medium', color: '#000000' }, bottom: { style: 'medium', color: '#000000' }, left: { style: 'medium', color: '#000000' }, right: { style: 'medium', color: '#000000' } } } },
        { row: 9, col: 2, ref: 'C10', value: 'mediumDashed', style: { borders: { top: { style: 'mediumDashed', color: '#C00000' }, bottom: { style: 'mediumDashed', color: '#C00000' }, left: { style: 'mediumDashed', color: '#C00000' }, right: { style: 'mediumDashed', color: '#C00000' } } } },
        { row: 9, col: 3, ref: 'D10', value: 'mediumDashDot', style: { borders: { top: { style: 'mediumDashDot', color: '#7030A0' }, bottom: { style: 'mediumDashDot', color: '#7030A0' }, left: { style: 'mediumDashDot', color: '#7030A0' }, right: { style: 'mediumDashDot', color: '#7030A0' } } } },
        { row: 9, col: 4, ref: 'E10', value: 'medDashDotDot', style: { borders: { top: { style: 'mediumDashDotDot', color: '#00B050' }, bottom: { style: 'mediumDashDotDot', color: '#00B050' }, left: { style: 'mediumDashDotDot', color: '#00B050' }, right: { style: 'mediumDashDotDot', color: '#00B050' } } } },
        { row: 9, col: 5, ref: 'F10', value: 'slantDashDot', style: { borders: { top: { style: 'slantDashDot', color: '#FFC000' }, bottom: { style: 'slantDashDot', color: '#FFC000' }, left: { style: 'slantDashDot', color: '#FFC000' }, right: { style: 'slantDashDot', color: '#FFC000' } } } },

        { row: 10, col: 0, ref: 'A11', value: 'thick', style: { borders: { top: { style: 'thick', color: '#000000' }, bottom: { style: 'thick', color: '#000000' }, left: { style: 'thick', color: '#000000' }, right: { style: 'thick', color: '#000000' } } } },
        { row: 10, col: 1, ref: 'B11', value: '混合边框', style: { borders: { top: { style: 'thick', color: '#FF0000' }, right: { style: 'dashed', color: '#0000FF' }, bottom: { style: 'dotted', color: '#00B050' }, left: { style: 'double', color: '#7030A0' } } } },

        // Row 12: 数字格式
        { row: 12, col: 0, ref: 'A13', value: '数字格式: #,##0.00', style: { font: { size: 10, color: '#666666' } } },
        { row: 12, col: 1, ref: 'B13', value: 1234567.89, style: { numberFormat: '#,##0.00', align: 'right' } },
        { row: 12, col: 2, ref: 'C13', value: 0.156, style: { numberFormat: '0.00%', align: 'right' } },
        { row: 12, col: 3, ref: 'D13', value: 45292.0, style: { numberFormat: 'yyyy年MM月dd日', align: 'center' } },
      ],
    },

    // Sheet 2: 尺寸测试
    {
      id: 'style-sheet-2',
      name: '尺寸测试',
      rowCount: 10,
      columnCount: 6,
      defaultRowHeight: 20,
      defaultColumnWidth: 72,
      rows: [
        { index: 0, height: 60, hidden: false },
        { index: 1, height: 40, hidden: false },
        { index: 2, height: 20, hidden: false },
        { index: 4, height: 30, hidden: true },
      ],
      columns: [
        { index: 0, width: 200, hidden: false },
        { index: 1, width: 60, hidden: false },
        { index: 2, width: 150, hidden: false },
        { index: 4, width: 100, hidden: true },
      ],
      merges: [
        { startRow: 0, startCol: 0, rowSpan: 2, colSpan: 2 },
        { startRow: 3, startCol: 0, rowSpan: 1, colSpan: 3 },
      ],
      cells: [
        { row: 0, col: 0, ref: 'A1', value: '2×2 合并区域',
          style: { font: { bold: true, size: 14, color: '#FFFFFF' }, align: 'center', valign: 'middle', fill: '#4472C4',
            borders: { top: { style: 'medium', color: '#1F4E79' }, bottom: { style: 'medium', color: '#1F4E79' }, left: { style: 'medium', color: '#1F4E79' }, right: { style: 'medium', color: '#1F4E79' } } } },
        { row: 0, col: 2, ref: 'C1', value: '普通单元格', style: { align: 'center', valign: 'middle', fill: '#BDD7EE' } },
        { row: 2, col: 0, ref: 'A3', value: '默认行高(20px)', style: { font: { size: 10 } } },
        { row: 2, col: 1, ref: 'B3', value: '窄列(60px)' },
        { row: 2, col: 2, ref: 'C3', value: '宽列(150px)', style: { fill: '#E2EFDA' } },
        { row: 3, col: 0, ref: 'A4', value: '横向 3 列合并',
          style: { font: { italic: true, size: 12, color: '#4472C4' }, align: 'center', valign: 'middle', fill: '#FCE4D6',
            borders: { top: { style: 'dashDot', color: '#ED7D31' }, bottom: { style: 'dashDot', color: '#ED7D31' }, left: { style: 'dashDot', color: '#ED7D31' }, right: { style: 'dashDot', color: '#ED7D31' } } } },
        { row: 5, col: 0, ref: 'A6', value: '隐藏行上方', style: { fill: '#FFF2CC' } },
        { row: 6, col: 0, ref: 'A7', value: '隐藏行下方（第5行已隐藏）', style: { fill: '#FFF2CC' } },
      ],
    },
  ],
};
