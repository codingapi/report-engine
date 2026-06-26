import { Button, Form, Input, Select, App as AntdApp } from 'antd';
import { useState } from 'react';
import type { ConnectionFormProps, DataSourceConfig, DataSourceType } from '@/types';

const DATASOURCE_TYPE_OPTIONS: Array<{ label: string; value: DataSourceType }> = [
  { label: 'DB', value: 'DB' },
  { label: 'EXCEL', value: 'EXCEL' },
  { label: 'CSV', value: 'CSV' },
];

/**
 * 数据源连接配置表单（受控 + antd Form）。
 * 字段：name / type / url / username / password / options(JSON 文本)
 * 「测试连接」依赖注入的 service；未注入时按钮禁用。
 */
export default function ConnectionForm({
  value,
  onChange,
  service,
  testResult,
  onTestResultChange,
  disabled,
}: ConnectionFormProps) {
  const { message } = AntdApp.useApp();
  const [testing, setTesting] = useState(false);

  const handleValuesChange = (_: unknown, all: Partial<DataSourceConfig>) => {
    onChange?.(all);
  };

  const handleTest = async () => {
    if (!service?.testConnection || !value) {
      onTestResultChange?.(null);
      return;
    }
    setTesting(true);
    try {
      const result = await service.testConnection({
        type: value.type,
        url: value.url,
        username: value.username,
        password: value.password,
        options: value.options,
      });
      onTestResultChange?.(result);
      if (result.ok) {
        message.success('连接成功');
      } else {
        message.error(result.message ?? '连接失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      onTestResultChange?.({ ok: false, message: msg });
      message.error(msg);
    } finally {
      setTesting(false);
    }
  };

  const merged: Partial<DataSourceConfig> = value ?? {};
  const canTest = !!service?.testConnection && !!value?.type && !disabled && !testing;

  return (
    <Form
      layout="vertical"
      initialValues={merged}
      onValuesChange={handleValuesChange}
      disabled={disabled}
    >
      <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
        <Input placeholder="数据源名称" />
      </Form.Item>
      <Form.Item label="类型" name="type" rules={[{ required: true, message: '请选择类型' }]}>
        <Select options={DATASOURCE_TYPE_OPTIONS} placeholder="选择数据源类型" />
      </Form.Item>
      <Form.Item label="连接 URL" name="url">
        <Input placeholder="JDBC URL / CSV 路径 / JSON URL / API endpoint" />
      </Form.Item>
      <Form.Item label="用户名" name="username">
        <Input autoComplete="off" />
      </Form.Item>
      <Form.Item label="密码 / Token" name="password">
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item label="额外选项 (JSON)" name="options">
        <Input.TextArea rows={2} placeholder='{"key":"value"}' />
      </Form.Item>
      <Form.Item>
        <Button loading={testing} disabled={!canTest} onClick={handleTest}>
          测试连接
        </Button>
        {testResult ? (
          <span style={{ marginLeft: 12, color: testResult.ok ? 'green' : 'red' }}>
            {testResult.ok ? '连接成功' : `失败：${testResult.message ?? ''}`}
          </span>
        ) : null}
      </Form.Item>
    </Form>
  );
}
