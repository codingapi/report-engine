import React, { useMemo } from 'react';
import { Modal, Form, Select, Input, Button, Popconfirm, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { DataConfig } from '../datasource/types';
import type { LoopBlockConfig } from '../properties/types';

interface LoopBlockModalProps {
  open: boolean;
  config: LoopBlockConfig | null;
  dataConfig?: DataConfig;
  onSave: (config: LoopBlockConfig) => void;
  onRemove: (id: string) => void;
  onClose: () => void;
}

const LoopBlockModal: React.FC<LoopBlockModalProps> = ({
  open,
  config,
  dataConfig,
  onSave,
  onRemove,
  onClose,
}) => {
  const [form] = Form.useForm();

  /** 字段选项：按表分组 */
  const fieldOptions = useMemo(() => {
    if (!dataConfig) return [];
    return dataConfig.tables.map((table) => ({
      label: table.alias || table.name,
      options: table.fields.map((field) => ({
        label: field.alias || field.name,
        value: `${table.name}.${field.name}`,
      })),
    }));
  }, [dataConfig]);

  /** 弹窗打开时同步表单值 */
  const handleAfterOpen = (isOpen: boolean) => {
    if (isOpen && config) {
      form.setFieldsValue({
        label: config.label || '',
        loopVariable: config.loopVariable || '',
      });
    }
  };

  const handleOk = async () => {
    if (!config) return;
    try {
      const values = await form.validateFields();
      onSave({
        ...config,
        label: values.label,
        loopVariable: values.loopVariable,
      });
    } catch {
      // 表单验证失败
    }
  };

  return (
    <Modal
      title="循环块配置"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      afterOpenChange={handleAfterOpen}
      destroyOnHidden
      footer={(
        <Space>
          {config && (
            <Popconfirm
              title="确定移除该循环块？"
              onConfirm={() => onRemove(config.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button danger icon={<DeleteOutlined />}>
                移除循环块
              </Button>
            </Popconfirm>
          )}
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleOk}>保存</Button>
        </Space>
      )}
    >
      {config && (
        <div style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
          范围：第 {config.startRow + 1} 行 ~ 第 {config.endRow + 1} 行，
          第 {config.startColumn + 1} 列 ~ 第 {config.endColumn + 1} 列
        </div>
      )}
      <Form form={form} layout="vertical" size="small">
        <Form.Item
          label="循环块标签"
          name="label"
        >
          <Input placeholder="如：部门循环、月份循环" />
        </Form.Item>
        <Form.Item
          label="循环变量字段"
          name="loopVariable"
          rules={[{ required: true, message: '请选择循环变量字段' }]}
        >
          <Select
            placeholder="选择循环迭代的字段"
            options={fieldOptions}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default LoopBlockModal;
