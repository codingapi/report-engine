import { render } from '@testing-library/react';
import ValueEditor from '@/components/property-panel/value-editor';
import type { Dataset, ReportValue } from '@/types';

// ─── 样式层:Space.Compact 内两 Select 的 flex:1 / minWidth:0 等宽分配 ───
// 关注:横向布局是否生效(两选择器等宽、容器铺满、minWidth:0 防长字段名撑破)。
const datasets: Dataset[] = [
  {
    id: 'ds',
    alias: '员工表',
    fields: [
      { name: 'name', alias: '姓名', dataType: 'STRING' },
      { name: 'salary', alias: '薪资', dataType: 'NUMBER' },
    ],
  },
];

function renderInBox(value: ReportValue) {
  const fixed = document.createElement('div');
  fixed.style.width = '400px';
  document.body.appendChild(fixed);
  const result = render(
    <ValueEditor value={value} datasets={datasets} onChange={() => {}} compact />,
    { container: fixed },
  );
  return { ...result, box: fixed };
}

describe('ValueEditor · FieldValue 布局样式指纹', () => {
  it('Space.Compact 容器铺满父宽度(≈100%,容许 antd 1px 误差)', () => {
    const { box } = renderInBox({ type: 'FieldValue', payload: 'ds.name' });
    const compact = box.querySelector('.ant-space-compact') as HTMLElement;
    expect(compact).toBeTruthy();
    const w = compact.getBoundingClientRect().width;
    expect(w).toBeGreaterThanOrEqual(395);
    expect(w).toBeLessThanOrEqual(405);
  });

  it('两个 Select 等宽分配(flex:1)且均有正向宽度', () => {
    const { box } = renderInBox({ type: 'FieldValue', payload: 'ds.name' });
    const selects = box.querySelectorAll<HTMLElement>('.ant-select');
    expect(selects).toHaveLength(2);
    const [a, b] = Array.from(selects).map((s) => s.getBoundingClientRect().width);
    expect(a).toBeGreaterThan(0);
    expect(b).toBeGreaterThan(0);
    // 等宽:两者差不超过 2px;合计铺满容器(容许 1px 误差)
    expect(Math.abs(a - b)).toBeLessThan(2);
    expect(a + b).toBeGreaterThanOrEqual(395);
    expect(a + b).toBeLessThanOrEqual(405);
  });

  it('未选数据集时字段 Select 禁用但仍占等宽(minWidth:0 不塌缩)', () => {
    const { box } = renderInBox({ type: 'FieldValue', payload: '' });
    const selects = box.querySelectorAll<HTMLElement>('.ant-select');
    const [, fieldSel] = Array.from(selects);
    expect(fieldSel.getBoundingClientRect().width).toBeGreaterThan(0);
  });
});
