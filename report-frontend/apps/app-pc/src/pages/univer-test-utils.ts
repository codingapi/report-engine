/**
 * Univer 验证页面 — 测试数据
 *
 * extractSnapshot / renderSnapshot 等工具已迁移至 @coding-report/report-univer
 * 此文件仅保留演示用的 MOCK_SNAPSHOT 数据
 */

import type { CellProp } from '@coding-report/report-univer';

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
