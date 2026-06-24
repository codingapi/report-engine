import { useEffect, useState } from 'react';
import { Modal, Form, Input, Select, message } from 'antd';
import type { ReportParam, DataType } from '@/types';
import { DATA_TYPE_OPTIONS } from '@/types';

interface ParamModalProps {
  open: boolean;
  /** 编辑模式：传入已有参数（预填表单，name 不可改）；新增模式：null */
  editingParam: ReportParam | null;
  /** 已有参数名列表，用于唯一性校验 */
  existingNames: string[];
  onClose: () => void;
  onConfirm: (param: ReportParam) => void;
}

const NAME_RE = /^[a-zA-Z_]\w*$/;

/** 参数添加/编辑弹窗 */
const ParamModal: React.FC<ParamModalProps> = ({
  open,
  editingParam,
  existingNames,
  onClose,
  onConfirm,
}) => {
  const isEdit = editingParam !== null;

  const [name, setName] = useState('');
  const [dataType, setDataType] = useState<DataType>('STRING');
  const [alias, setAlias] = useState('');
  const [defaultValue, setDefaultValue] = useState('');

  useEffect(() => {
    if (open) {
      if (editingParam) {
        setName(editingParam.name);
        setDataType(editingParam.dataType);
        setAlias(editingParam.alias ?? '');
        setDefaultValue(editingParam.defaultValue ?? '');
      } else {
        setName('');
        setDataType('STRING');
        setAlias('');
        setDefaultValue('');
      }
    }
  }, [open, editingParam]);

  const handleOk = () => {
    if (!name) {
      message.error('请输入参数名');
      return;
    }
    if (!NAME_RE.test(name)) {
      message.error('参数名只能包含字母、数字和下划线，且以字母或下划线开头');
      return;
    }
    const isDuplicate = existingNames.some((n) => n === name && n !== editingParam?.name);
    if (isDuplicate) {
      message.error('参数名已存在');
      return;
    }

    const param: ReportParam = {
      id: editingParam?.id ?? '',
      name,
      alias: alias || undefined,
      dataType,
      defaultValue: defaultValue || undefined,
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
      width={480}
    >
      <Form layout="vertical" size="small">
        <Form.Item label="参数名" tooltip="在表达式中以 ${参数名} 引用，需唯一">
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="如 companyName"
            disabled={isEdit}
          />
        </Form.Item>

        <Form.Item label="数据类型" tooltip="决定导出预览时的输入控件类型">
          <Select value={dataType} onChange={setDataType} options={DATA_TYPE_OPTIONS} />
        </Form.Item>

        <Form.Item label="别名" tooltip="可选的中文名称，便于识别">
          <Input
            value={alias}
            onChange={(e) => setAlias(e.target.value)}
            placeholder="如 公司名称"
          />
        </Form.Item>

        <Form.Item label="默认值" tooltip="渲染时若外部未传值则使用此值；留空则导出时必须传入">
          <Input
            value={defaultValue}
            onChange={(e) => setDefaultValue(e.target.value)}
            placeholder="可选"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ParamModal;
