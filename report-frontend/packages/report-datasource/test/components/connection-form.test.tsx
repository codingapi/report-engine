import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import ConnectionForm from '@/components/connection-form';
import type { DatasourceService } from '@/types';

function renderForm(props: Parameters<typeof ConnectionForm>[0] = {}) {
  return render(
    <AntdApp>
      <ConnectionForm {...props} />
    </AntdApp>,
  );
}

describe('ConnectionForm', () => {
  it('渲染所有字段标签', () => {
    renderForm();
    expect(screen.getByText('名称')).toBeInTheDocument();
    expect(screen.getByText('类型')).toBeInTheDocument();
    expect(screen.getByText('连接 URL')).toBeInTheDocument();
    expect(screen.getByText('用户名')).toBeInTheDocument();
    expect(screen.getByText('密码 / Token')).toBeInTheDocument();
    expect(screen.getByText('额外选项 (JSON)')).toBeInTheDocument();
  });

  it('未注入 service 时「测试连接」按钮禁用', () => {
    renderForm({ value: { type: 'DB', name: 'test' } });
    expect(screen.getByRole('button', { name: '测试连接' })).toBeDisabled();
  });

  it('注入 service 但缺 type 时按钮仍禁用', () => {
    const service: DatasourceService = {
      testConnection: async () => ({ ok: true }),
      exploreTables: async () => [],
      exploreColumns: async () => [],
    };
    renderForm({ service, value: { name: 'test' } });
    expect(screen.getByRole('button', { name: '测试连接' })).toBeDisabled();
  });

  it('service + type 齐备时按钮可点击，触发 testConnection', async () => {
    const testConnection = rs.fn(async () => ({ ok: true, message: 'ok' }));
    const service: DatasourceService = {
      testConnection,
      exploreTables: async () => [],
      exploreColumns: async () => [],
    };
    const onTestResultChange = rs.fn();
    renderForm({
      service,
      value: { type: 'DB', name: 'test', url: 'jdbc:x' },
      onTestResultChange,
    });
    const btn = screen.getByRole('button', { name: '测试连接' });
    expect(btn).not.toBeDisabled();
    await userEvent.click(btn);
    expect(testConnection).toHaveBeenCalledTimes(1);
    expect(onTestResultChange).toHaveBeenCalledWith({ ok: true, message: 'ok' });
  });
});
