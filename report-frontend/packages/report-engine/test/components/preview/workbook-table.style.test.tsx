import { render, screen } from '@testing-library/react';
import { SheetTable, WorkbookTable } from '@/components/preview/workbook-table';
import type { ExcelSheet, ExcelWorkbook } from '@coding-report/report-univer';

// ─── 样式层:预览表格几何(列宽 / 行高 / 隐藏列行 / 合并 span / 空态 / 反查) ───

const sheet: ExcelSheet = {
  id: 's1',
  name: '工资表',
  rowCount: 5,
  columnCount: 5,
  defaultRowHeight: 24,
  defaultColumnWidth: 88,
  merges: [],
  cells: [
    { row: 0, col: 0, ref: 'A1', value: '姓名' },
    { row: 0, col: 1, ref: 'B1', value: '薪资' },
    { row: 1, col: 0, ref: 'A2', value: '张三' },
    { row: 1, col: 1, ref: 'B2', value: 5000 },
  ],
  rows: [{ index: 0, height: 40, hidden: false }],
  columns: [
    { index: 0, width: 200, hidden: false },
    { index: 1, width: 88, hidden: true },
  ],
};

describe('SheetTable · 列宽与行高几何', () => {
  it('tableLayout fixed + borderCollapse collapse', () => {
    const { container } = render(<SheetTable sheet={sheet} />);
    const table = container.querySelector('table')!;
    const cs = window.getComputedStyle(table);
    expect(cs.tableLayout).toBe('fixed');
    expect(cs.borderCollapse).toBe('collapse');
  });

  it('列宽:显式列用 columns 宽度,缺省用 defaultColumnWidth', () => {
    const { container } = render(<SheetTable sheet={sheet} />);
    const cols = container.querySelectorAll('col');
    expect(cols).toHaveLength(2); // maxCol+1 = 2
    expect((cols[0] as HTMLElement).style.width).toBe('200px');
    expect((cols[1] as HTMLElement).style.width).toBe('0px'); // hidden → 0
  });

  it('行高:显式行用 rows 高度,缺省用 defaultRowHeight', () => {
    const { container } = render(<SheetTable sheet={sheet} />);
    const trs = container.querySelectorAll('tbody > tr');
    expect(trs).toHaveLength(2); // maxRow+1 = 2
    expect((trs[0] as HTMLElement).style.height).toBe('40px');
    expect((trs[1] as HTMLElement).style.height).toBe('24px'); // default
  });

  it('隐藏行不渲染 tr', () => {
    const hiddenRowSheet: ExcelSheet = {
      ...sheet,
      rows: [{ index: 1, height: 24, hidden: true }],
    };
    const { container } = render(<SheetTable sheet={hiddenRowSheet} />);
    // row 1 hidden → 仅 row 0 渲染
    expect(container.querySelectorAll('tbody > tr')).toHaveLength(1);
  });
});

describe('SheetTable · 合并单元格 span', () => {
  const merged: ExcelSheet = {
    id: 's2',
    name: 'M',
    rowCount: 5,
    columnCount: 5,
    defaultRowHeight: 24,
    defaultColumnWidth: 88,
    merges: [{ startRow: 0, startCol: 0, rowSpan: 2, colSpan: 2 }],
    cells: [{ row: 0, col: 0, ref: 'A1', value: '合并标题' }],
    rows: [],
    columns: [],
  };

  it('锚点格携带 rowSpan/colSpan,覆盖格不渲染 td', () => {
    const { container } = render(<SheetTable sheet={merged} />);
    const trs = container.querySelectorAll('tbody > tr');
    // 2x2 区域:row0 只有锚点 td,row0 的 (0,1) covered;row1 (1,0)(1,1) covered
    const row0Tds = trs[0].querySelectorAll('td');
    expect(row0Tds).toHaveLength(1);
    expect((row0Tds[0] as HTMLTableCellElement).rowSpan).toBe(2);
    expect((row0Tds[0] as HTMLTableCellElement).colSpan).toBe(2);
    expect(trs[1].querySelectorAll('td')).toHaveLength(0);
  });
});

describe('SheetTable · 反查格样式', () => {
  it('drillable 单元格:蓝色文字 + pointer 光标', () => {
    const { container } = render(
      <SheetTable sheet={sheet} drillable={new Set(['1:0'])} onDrill={() => {}} />,
    );
    const td = container.querySelector('tbody tr:nth-child(2) td') as HTMLElement;
    const cs = window.getComputedStyle(td);
    // #1677ff → rgb(22, 119, 255)
    expect(cs.color).toBe('rgb(22, 119, 255)');
    expect(cs.cursor).toBe('pointer');
  });
});

describe('WorkbookTable · sheet 数量分支', () => {
  it('无有效 sheet → 居中占位(minHeight 240 / flex 居中)', () => {
    const wb: ExcelWorkbook = { sheets: [] };
    const { container } = render(<WorkbookTable workbook={wb} />);
    // 外层 flex 容器是 .ant-empty 的父级(closest('div') 会取到 Empty 内层包裹)
    const empty = screen.getByText('无预览数据').closest('.ant-empty')!.parentElement!;
    const cs = window.getComputedStyle(empty);
    expect(cs.display).toBe('flex');
    expect(cs.minHeight).toBe('240px');
    expect(cs.justifyContent).toBe('center');
    expect(cs.alignItems).toBe('center');
  });

  it('单个 sheet → 直接渲染 table(无 Tabs)', () => {
    const wb: ExcelWorkbook = { sheets: [sheet] };
    const { container } = render(<WorkbookTable workbook={wb} />);
    expect(container.querySelector('table')).toBeTruthy();
    // 无 Tabs(无 .ant-tabs)
    expect(container.querySelector('.ant-tabs')).toBeNull();
  });

  it('多个 sheet → Tabs 切换,默认展示第一个 sheet 的表格', () => {
    const wb: ExcelWorkbook = {
      sheets: [sheet, { ...sheet, id: 's2', name: '第二表' }],
    };
    const { container } = render(<WorkbookTable workbook={wb} />);
    expect(container.querySelector('.ant-tabs')).toBeTruthy();
    // 默认激活第一个 tab,其表格存在
    expect(container.querySelector('table')).toBeTruthy();
  });
});
