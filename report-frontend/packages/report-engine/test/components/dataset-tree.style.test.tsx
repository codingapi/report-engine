import { render, screen } from '@testing-library/react';
import DatasetTree from '@/components/dataset-tree';
import type { Dataset } from '@/types';

const datasets: Dataset[] = [
  {
    id: 'ds',
    alias: '员工表',
    sourceType: 'CSV',
    fields: [
      { name: 'id', alias: 'ID', dataType: 'NUMBER', primaryKey: true },
      { name: 'name', alias: '姓名', dataType: 'STRING' },
    ],
  },
];

// ─── 样式层:数据源 Tag 小标签字号 + PK 图标几何 ───
describe('DatasetTree · 样式指纹', () => {
  it('数据源 Tag 为小标签:fontSize 10px', () => {
    render(<DatasetTree datasets={datasets} />);
    const tag = screen.getByText('CSV').closest('.ant-tag') as HTMLElement;
    expect(tag).toBeTruthy();
    const cs = window.getComputedStyle(tag);
    expect(cs.fontSize).toBe('10px');
  });

  it('主键字段渲染 KeyOutlined 图标,具有正向几何尺寸', () => {
    render(<DatasetTree datasets={datasets} />);
    // 字段行内有 .re-field-pk 图标
    const pk = document.querySelector('.re-field-pk') as HTMLElement;
    expect(pk).toBeTruthy();
    const rect = pk.getBoundingClientRect();
    expect(rect.width).toBeGreaterThan(0);
    expect(rect.height).toBeGreaterThan(0);
  });

  it('非主键字段不渲染 PK 图标', () => {
    render(<DatasetTree datasets={datasets} />);
    // 只有一个 PK 图标(id 字段)
    expect(document.querySelectorAll('.re-field-pk')).toHaveLength(1);
  });
});
