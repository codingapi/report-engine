import { useEffect, useState } from 'react';
import { Modal, Form, Input, InputNumber, Switch, message } from 'antd';
import type { ParamDTO } from '@/types';

interface ParamInputModalProps {
  params: ParamDTO[];
  open: boolean;
  onConfirm: (values: Record<string, unknown>) => void;
  onCancel: () => void;
}

/** 导出/预览前的参数输入弹窗 */
const ParamInputModal: React.FC<ParamInputModalProps> = ({ params, open, onConfirm, onCancel }) => {
  const [values, setValues] = useState<Record<string, unknown>>({});

  // 打开或参数变化时预填默认值
  useEffect(() => {
    if (!open) return;
    const init: Record<string, unknown> = {};
    for (const p of params) {
      if (p.defaultValue != null && p.defaultValue !== '') {
        init[p.name] =
          p.dataType === 'NUMBER'
            ? Number(p.defaultValue)
            : p.dataType === 'BOOLEAN'
              ? p.defaultValue === 'true'
              : p.defaultValue;
      }
    }
    setValues(init);
  }, [open, params]);

  const update = (name: string, val: unknown) => setValues((prev) => ({ ...prev, [name]: val }));

  const handleOk = () => {
    for (const p of params) {
      if (!p.defaultValue) {
        const v = values[p.name];
        if (v == null || v === '') {
          message.error(`请输入${p.alias || p.name}`);
          return;
        }
      }
    }
    onConfirm(values);
  };

  const renderField = (param: ParamDTO) => {
    const isRequired = !param.defaultValue;
    const label = param.alias || param.name;

    switch (param.dataType) {
      case 'NUMBER':
        return (
          <Form.Item key={param.name} label={label} required={isRequired}>
            <InputNumber
              style={{ width: '100%' }}
              value={values[param.name] as number | undefined}
              onChange={(v) => update(param.name, v)}
              placeholder={isRequired ? '必填' : '可选'}
            />
          </Form.Item>
        );

      case 'BOOLEAN':
        return (
          <Form.Item key={param.name} label={label}>
            <Switch checked={!!values[param.name]} onChange={(v) => update(param.name, v)} />
          </Form.Item>
        );

      default:
        return (
          <Form.Item key={param.name} label={label} required={isRequired}>
            <Input
              value={(values[param.name] as string) || ''}
              onChange={(e) => update(param.name, e.target.value)}
              placeholder={isRequired ? '必填' : '可选'}
            />
          </Form.Item>
        );
    }
  };

  return (
    <Modal
      title="输入报表参数"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
      width={480}
    >
      <Form layout="vertical" size="small">
        {params.map(renderField)}
      </Form>
    </Modal>
  );
};

export default ParamInputModal;
