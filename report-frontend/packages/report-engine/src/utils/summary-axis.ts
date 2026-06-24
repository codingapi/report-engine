import type { SummaryRow, SummaryAxis } from '@/types';

/** 汇总方向，缺省视为纵向（向后兼容旧配置）。 */
export const summaryAxis = (s: Pick<SummaryRow, 'axis'>): SummaryAxis => s.axis ?? 'VERTICAL';

/**
 * 汇总格在表格中的 (row, col)：
 * - 纵向：row = mainPos（声明行），col = crossPos（列）
 * - 横向：row = crossPos（行），col = mainPos（声明列）
 */
export const summaryCellRC = (
  s: Pick<SummaryRow, 'axis' | 'mainPos'>,
  crossPos: number,
): { row: number; col: number } =>
  summaryAxis(s) === 'HORIZONTAL'
    ? { row: crossPos, col: s.mainPos }
    : { row: s.mainPos, col: crossPos };

/** 某单元格 (row, col) 在汇总轴下的交叉坐标：纵向取列、横向取行。 */
export const crossPosOf = (axis: SummaryAxis, row: number, col: number): number =>
  axis === 'HORIZONTAL' ? row : col;

/** 某单元格 (row, col) 在汇总轴下的主轴坐标：纵向取行、横向取列。 */
export const mainPosOf = (axis: SummaryAxis, row: number, col: number): number =>
  axis === 'HORIZONTAL' ? col : row;

/** (row, col) 是否命中某汇总：主轴位置相符且交叉坐标落在 [crossFrom, crossTo] 内。 */
export const summaryHit = (s: SummaryRow, row: number, col: number): boolean => {
  const axis = summaryAxis(s);
  return (
    mainPosOf(axis, row, col) === s.mainPos &&
    crossPosOf(axis, row, col) >= s.crossFrom &&
    crossPosOf(axis, row, col) <= s.crossTo
  );
};
