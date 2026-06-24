import { render, screen, waitFor } from '@testing-library/react';
import DrillModal from '@/components/preview/drill-modal';
import type { DrillResult } from '@coding-report/report-api';

// ─── 样式层:反查明细弹窗 Modal 尺寸 / 滚动 / 空态 / 表格 ───
describe('DrillModal · 样式指纹', () => {
  it('Modal 宽度 80% 视口、zIndex 1050', async () => {
    render(<DrillModal open loading result={undefined} onClose={() => {}} />);
    const dialog = await screen.findByRole('dialog');
    // dialog 本身即 .ant-modal;读 computed width(不受动画 transform 影响)
    const cs = window.getComputedStyle(dialog);
    const vw = window.innerWidth;
    const w = parseFloat(cs.width);
    expect(w).toBeGreaterThan(vw * 0.78);
    expect(w).toBeLessThan(vw * 0.82);
    const wrap = dialog.closest('.ant-modal-wrap') as HTMLElement;
    const z = wrap.style.zIndex || window.getComputedStyle(wrap).zIndex;
    expect(z).toContain('1050');
  });

  it('Modal body 最大高度 70vh 且可滚动', async () => {
    render(<DrillModal open loading result={undefined} onClose={() => {}} />);
    const dialog = await screen.findByRole('dialog');
    const body = dialog.querySelector('.ant-modal-body') as HTMLElement;
    const cs = window.getComputedStyle(body);
    // 70vh 被解析为像素,断言 ≈ 0.7 * 视口高
    expect(parseFloat(cs.maxHeight)).toBeGreaterThan(window.innerHeight * 0.68);
    expect(parseFloat(cs.maxHeight)).toBeLessThan(window.innerHeight * 0.72);
    expect(cs.overflowY).toBe('auto');
  });

  it('加载态显示「加载中...」', () => {
    render(<DrillModal open loading result={undefined} onClose={() => {}} />);
    expect(screen.getByText('加载中...')).toBeInTheDocument();
  });

  it('空明细:result 无 rows 时显示无数据占位(padding:24 / 居中 / #999)', async () => {
    const result: DrillResult = {
      datasetId: 'ds',
      alias: '员工表',
      fields: [{ name: 'name', alias: '姓名' }],
      rows: [],
    };
    render(<DrillModal open loading={false} result={result} onClose={() => {}} />);
    const tip = await screen.findByText('无明细数据');
    const cs = window.getComputedStyle(tip);
    expect(cs.padding).toBe('24px');
    expect(cs.textAlign).toBe('center');
    expect(cs.color).toBe('rgb(153, 153, 153)');
  });

  it('有明细:Table 紧凑模式(size=small)且带分页器', async () => {
    const result: DrillResult = {
      datasetId: 'ds',
      alias: '员工表',
      fields: [{ name: 'name', alias: '姓名' }],
      rows: [{ name: '张三' }],
    };
    render(<DrillModal open loading={false} result={result} onClose={() => {}} />);
    await screen.findByRole('table');
    // Table 渲染在 Modal portal(document.body)内,不在 render 的 container 里,用 document 查询
    await waitFor(() => {
      expect(document.querySelector('.ant-table.ant-table-small')).toBeTruthy();
      expect(document.querySelector('.ant-pagination')).toBeTruthy();
    });
  });
});
