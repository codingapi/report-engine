import { render, screen, act } from '@testing-library/react';
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

// antd Tree 挂载后有异步状态更新，用 act 包裹 render 避免 act(...) 告警
async function renderTree() {
  await act(async () => {
    render(<DatasetTree datasets={datasets} />);
  });
}

// ─── 样式层:数据源 Tag 小标签字号 + PK 图标几何 ───
describe('DatasetTree · 样式指纹', () => {
  it('数据源 Tag 为小标签:fontSize 10px', async () => {
    await renderTree();
    const tag = screen.getByText('CSV').closest('.ant-tag') as HTMLElement;
    expect(tag).toBeTruthy();
    const cs = window.getComputedStyle(tag);
    expect(cs.fontSize).toBe('10px');
  });

  it('主键字段渲染 KeyOutlined 图标,具有正向几何尺寸', async () => {
    await renderTree();
    // 字段行内有 .re-field-pk 图标
    const pk = document.querySelector('.re-field-pk') as HTMLElement;
    expect(pk).toBeTruthy();
    const rect = pk.getBoundingClientRect();
    expect(rect.width).toBeGreaterThan(0);
    expect(rect.height).toBeGreaterThan(0);
  });

  it('非主键字段不渲染 PK 图标', async () => {
    await renderTree();
    // 只有一个 PK 图标(id 字段)
    expect(document.querySelectorAll('.re-field-pk')).toHaveLength(1);
  });
});
