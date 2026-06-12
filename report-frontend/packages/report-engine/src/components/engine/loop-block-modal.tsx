import React from 'react';
import { Modal, Form, Input, Button, Popconfirm, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { LoopBlockConfig } from '../properties/types';

interface LoopBlockModalProps {
  open: boolean;
  config: LoopBlockConfig | null;
  onSave: (config: LoopBlockConfig) => void;
  onRemove: (id: string) => void;
  onClose: () => void;
}

const LoopBlockModal: React.FC<LoopBlockModalProps> = ({
  open,
  config,
  onSave,
  onRemove,
  onClose,
}) => {
  const [form] = Form.useForm();

  /** 弹窗打开时同步表单值 */
  const handleAfterOpen = (isOpen: boolean) => {
    if (isOpen && config) {
      form.setFieldsValue({
        label: config.label || '',
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
      </Form>
    </Modal>
  );
};

export default LoopBlockModal;
