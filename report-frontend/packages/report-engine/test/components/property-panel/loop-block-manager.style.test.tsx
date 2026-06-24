import { render, screen } from '@testing-library/react';
import LoopBlockManager from '@/components/property-panel/loop-block-manager';
import type { Dataset, LoopBlock } from '@/types';

const datasets: Dataset[] = [
  { id: 'ds', alias: '员工表', fields: [{ name: 'name', alias: '姓名', dataType: 'STRING' }] },
];

const oneBlock: LoopBlock[] = [
  {
    id: 'lb1',
    label: '员工循环',
    sheetId: 's1',
    startRow: 0,
    startColumn: 0,
    endRow: 2,
    endColumn: 1,
    source: { datasetId: 'ds', filters: [], groupBy: [], orderBy: [] },
  },
];

// ─── 样式层:只读区域背景区分 + 空态居中与描述字号 ───
describe('LoopBlockManager · 样式指纹', () => {
  it('模板区域为只读 Input,背景填充 #f5f5f5 区分可编辑项', () => {
    const { container } = render(
      <LoopBlockManager loopBlocks={oneBlock} datasets={datasets} onChange={() => {}} />,
    );
    // Form.Item label="模板区域" 下的 Input
    const regionInput = screen.getByDisplayValue('A1:B3') as HTMLInputElement;
    expect(regionInput.readOnly).toBe(true);
    const cs = window.getComputedStyle(regionInput);
    // #f5f5f5 → rgb(245, 245, 245)
    expect(cs.backgroundColor).toBe('rgb(245, 245, 245)');
    // 确认取到的是 Input 而非外层包裹
    expect(container.querySelector('input')!).toBeTruthy();
  });

  it('空态:Empty 上下间距 24px,描述文字 13px / #999', () => {
    render(<LoopBlockManager loopBlocks={[]} datasets={datasets} onChange={() => {}} />);
    // 描述文本含 <br/> 多行,getByText 精确匹配失败,用正则
    const desc = screen.getByText(/暂无循环块/);
    const empty = desc.closest('.ant-empty') as HTMLElement;
    expect(empty).toBeTruthy();
    const emptyCs = window.getComputedStyle(empty);
    expect(emptyCs.marginTop).toBe('24px');
    expect(emptyCs.marginBottom).toBe('24px');

    const descCs = window.getComputedStyle(desc);
    expect(descCs.fontSize).toBe('13px');
    // #999 → rgb(153, 153, 153)
    expect(descCs.color).toBe('rgb(153, 153, 153)');
  });
});
