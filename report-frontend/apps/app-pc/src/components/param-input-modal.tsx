import { useEffect } from 'react';
import { Modal, Form, Input, InputNumber, Switch } from 'antd';
import type { ReportParam } from '@coding-report/report-engine';

interface ParamInputModalProps {
  params: ReportParam[];
  open: boolean;
  onConfirm: (values: Record<string, unknown>) => void;
  onCancel: () => void;
}

/** 导出/预览前的参数输入弹窗：为每个参数提供输入控件，无默认值的标记必填。 */
const ParamInputModal: React.FC<ParamInputModalProps> = ({
  params,
  open,
  onConfirm,
  onCancel,
}) => {
  const [form] = Form.useForm();

  useEffect(() => {
    if (open) {
      // 预填默认值
      const initialValues: Record<string, unknown> = {};
      for (const p of params) {
        if (p.defaultValue != null && p.defaultValue !== '') {
          initialValues[p.name] =
            p.dataType === 'NUMBER' ? Number(p.defaultValue) :
            p.dataType === 'BOOLEAN' ? p.defaultValue === 'true' :
            p.defaultValue;
        }
      }
      form.setFieldsValue(initialValues);
    }
  }, [open, params, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    onConfirm(values);
  };

  const renderField = (param: ReportParam) => {
    const isRequired = !param.defaultValue;

    switch (param.dataType) {
      case 'NUMBER':
        return (
          <Form.Item
            key={param.name}
            name={param.name}
            label={param.alias || param.name}
            rules={isRequired ? [{ required: true, message: `请输入${param.alias || param.name}` }] : []}
          >
            <InputNumber style={{ width: '100%' }} placeholder={isRequired ? '必填' : '可选'} />
          </Form.Item>
        );

      case 'BOOLEAN':
        return (
          <Form.Item
            key={param.name}
            name={param.name}
            label={param.alias || param.name}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        );

      default:
        // STRING / DATE / DATETIME
        return (
          <Form.Item
            key={param.name}
            name={param.name}
            label={param.alias || param.name}
            rules={isRequired ? [{ required: true, message: `请输入${param.alias || param.name}` }] : []}
          >
            <Input placeholder={isRequired ? '必填' : '可选'} />
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
      destroyOnClose
      width={480}
      okText="确认并导出"
      cancelText="取消"
    >
      <Form form={form} layout="vertical" autoComplete="off">
        {params.map(renderField)}
      </Form>
    </Modal>
  );
};

export default ParamInputModal;
