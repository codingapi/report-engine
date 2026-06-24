import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DrillEditor from '@/components/property-panel/drill-editor';
import type { Dataset } from '@/types';

const datasets: Dataset[] = [
  { id: 'ds-1', alias: '员工表', fields: [] },
  { id: 'ds-2', alias: '商品表', fields: [] },
];

const ALERT_TEXT = '视图后续将与数据源一样独立管理配置；本期直接用数据集本身作默认视图。';

/**
 * 受控渲染 DrillEditor：onChange 回调被 spy 的同时驱动内部状态，
 * 这样既能断言回调 payload，又能验证条件渲染随状态切换。
 */
function renderDrill(
  initial: { drillEnabled?: boolean; drillView?: string | null; defaultView?: string | null } = {},
) {
  const onChange = rs.fn();
  function Harness() {
    const [state, setState] = useState({
      drillEnabled: initial.drillEnabled ?? false,
      drillView: initial.drillView ?? null,
    });
    return (
      <DrillEditor
        datasets={datasets}
        defaultView={initial.defaultView ?? null}
        drillEnabled={state.drillEnabled}
        drillView={state.drillView}
        onChange={(patch) => {
          onChange(patch);
          setState((s) => ({ ...s, ...patch }));
        }}
      />
    );
  }
  return { onChange, ...render(<Harness />) };
}

describe('DrillEditor', () => {
  it('默认关闭：仅渲染反查开关，不渲染反查视图', () => {
    renderDrill();
    expect(screen.getByRole('switch')).not.toBeChecked();
    expect(screen.queryByText('反查视图')).toBeNull();
    expect(screen.queryByText(ALERT_TEXT)).toBeNull();
  });

  it('开启反查：触发 onChange({drillEnabled:true}) 并渲染反查视图与提示', async () => {
    const { onChange } = renderDrill();
    const user = userEvent.setup();

    await user.click(screen.getByRole('switch'));

    expect(onChange).toHaveBeenCalledWith({ drillEnabled: true });
    expect(await screen.findByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText('反查视图')).toBeInTheDocument();
    expect(screen.getByText(ALERT_TEXT)).toBeInTheDocument();
  });

  it('再次关闭：触发 onChange({drillEnabled:false})，反查视图消失', async () => {
    const { onChange } = renderDrill();
    const user = userEvent.setup();
    const sw = screen.getByRole('switch');

    await user.click(sw); // 开启
    await screen.findByRole('combobox');
    await user.click(sw); // 关闭

    expect(onChange).toHaveBeenCalledWith({ drillEnabled: false });
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.queryByText(ALERT_TEXT)).toBeNull();
  });

  it('defaultView 命中时，反查视图占位文本含该数据集别名', () => {
    // antd Select 的 placeholder 渲染在独立 div（非 input 属性），按文本查询。
    renderDrill({ drillEnabled: true, defaultView: 'ds-1' });
    expect(screen.getByText(/默认（员工表）/)).toBeInTheDocument();
  });
});

// ─── 结构层 / 可访问性（ARIA 语义断言，确定性、无基准文件） ───
describe('DrillEditor · 结构与可访问性', () => {
  it('反查开关具备正确的 switch 语义', () => {
    renderDrill();
    const sw = screen.getByRole('switch');
    // 未开启：aria-checked=false（happy-dom 下 jest-dom toBeChecked 映射 aria-checked）
    expect(sw).not.toBeChecked();
    expect(sw).toHaveAttribute('aria-checked', 'false');
  });

  it('「反查」「反查视图」标签以可访问的 label 文本呈现', () => {
    renderDrill({ drillEnabled: true });
    expect(screen.getByText('反查')).toBeInTheDocument();
    expect(screen.getByText('反查视图')).toBeInTheDocument();
  });

  it('开启后：Alert 有 alert 角色、Select 有 combobox 角色且默认收起', () => {
    renderDrill({ drillEnabled: true });
    // Alert → role=alert（aria-live 区域，可被读屏播报）
    expect(screen.getByRole('alert')).toBeInTheDocument();
    // Select → combobox，未点击下拉 → aria-expanded=false
    const combo = screen.getByRole('combobox');
    expect(combo).toHaveAttribute('aria-expanded', 'false');
    expect(combo).toHaveAttribute('aria-haspopup', 'listbox');
  });

  it('关闭时：DOM 中不存在 alert 与 combobox（条件渲染守门）', () => {
    renderDrill({ drillEnabled: false });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByRole('combobox')).toBeNull();
  });
});
