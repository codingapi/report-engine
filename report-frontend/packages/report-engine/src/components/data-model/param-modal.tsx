import { useEffect } from 'react';
import { Modal, Form, Input, Select } from 'antd';
import type { ReportParam } from '../../types';
import { DATA_TYPE_OPTIONS } from '../../types';

interface ParamModalProps {
  open: boolean;
  /** 编辑模式：传入已有参数（预填表单，name 不可改）；新增模式：null */
  editingParam: ReportParam | null;
  /** 已有参数名列表，用于唯一性校验 */
  existingNames: string[];
  onClose: () => void;
  onConfirm: (param: ReportParam) => void;
}

/** 参数添加/编辑弹窗：四字段表单（name / alias / dataType / defaultValue）。 */
const ParamModal: React.FC<ParamModalProps> = ({
  open,
  editingParam,
  existingNames,
  onClose,
  onConfirm,
}) => {
  const [form] = Form.useForm();
  const isEdit = editingParam !== null;

  useEffect(() => {
    if (open) {
      if (editingParam) {
        form.setFieldsValue({
          name: editingParam.name,
          alias: editingParam.alias ?? '',
          dataType: editingParam.dataType,
          defaultValue: editingParam.defaultValue ?? '',
        });
      } else {
        form.resetFields();
      }
    }
  }, [open, editingParam, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const param: ReportParam = {
      id: editingParam?.id ?? '',
      name: values.name,
      alias: values.alias || undefined,
      dataType: values.dataType,
      defaultValue: values.defaultValue || undefined,
    };
    onConfirm(param);
  };

  return (
    <Modal
      title={isEdit ? '编辑参数' : '添加参数'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      destroyOnHidden
      width={420}
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <Form.Item
          name="name"
          label="参数名"
          rules={[
            { required: true, message: '请输入参数名' },
            {
              pattern: /^[a-zA-Z_]\w*$/,
              message: '只能包含字母、数字和下划线，且以字母或下划线开头',
            },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value) return Promise.resolve();
                const isDuplicate = existingNames.some(
                  (n) => n === value && n !== editingParam?.name,
                );
                if (isDuplicate) {
                  return Promise.reject(new Error('参数名已存在'));
                }
                return Promise.resolve();
              },
            }),
          ]}
          extra="在表达式中以 ${参数名} 引用"
        >
          <Input placeholder="如 companyName" disabled={isEdit} />
        </Form.Item>

        <Form.Item
          name="alias"
          label="别名"
          extra="中文名称，在表达式选择界面展示"
        >
          <Input placeholder="如 公司名称" />
        </Form.Item>

        <Form.Item
          name="dataType"
          label="数据类型"
          rules={[{ required: true, message: '请选择数据类型' }]}
          initialValue="STRING"
        >
          <Select options={DATA_TYPE_OPTIONS} />
        </Form.Item>

        <Form.Item
          name="defaultValue"
          label="默认值"
          extra="渲染时若外部未传值则使用此值；留空则导出时必须传入"
        >
          <Input placeholder="可选" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ParamModal;
