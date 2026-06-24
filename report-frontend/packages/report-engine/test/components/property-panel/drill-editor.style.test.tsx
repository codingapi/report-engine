import { render, screen } from '@testing-library/react';
import DrillEditor from '@/components/property-panel/drill-editor';
import type { Dataset } from '@/types';

const datasets: Dataset[] = [{ id: 'ds-1', alias: '员工表', fields: [] }];

// ─── 样式层（真浏览器 chromium headless）：computed style + 几何指纹，不截图 ───
// 原则：显式声明在乎哪些属性（marginTop / display / 正向几何尺寸），读成数据断言。
describe('DrillEditor · 样式指纹', () => {
  it('Alert 内联样式生效：顶部间距 8px 且可见', () => {
    render(<DrillEditor drillEnabled datasets={datasets} defaultView="ds-1" onChange={() => {}} />);
    const alert = screen.getByRole('alert');
    const cs = window.getComputedStyle(alert);
    expect(cs.marginTop).toBe('8px');
    expect(cs.display).not.toBe('none');
  });

  it('反查开关具有正向几何尺寸', () => {
    render(<DrillEditor drillEnabled={false} datasets={datasets} onChange={() => {}} />);
    const sw = screen.getByRole('switch');
    const rect = sw.getBoundingClientRect();
    expect(rect.width).toBeGreaterThan(0);
    expect(rect.height).toBeGreaterThan(0);
  });

  it('开启反查：Alert 落在反查开关下方（垂直顺序）', () => {
    render(<DrillEditor drillEnabled datasets={datasets} defaultView="ds-1" onChange={() => {}} />);
    const sw = screen.getByRole('switch').getBoundingClientRect();
    const alert = screen.getByRole('alert').getBoundingClientRect();
    expect(alert.top).toBeGreaterThanOrEqual(sw.bottom);
  });
});
