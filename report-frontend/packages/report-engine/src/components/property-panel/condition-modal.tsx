import { useEffect, useState } from 'react';
import { Modal, Select } from 'antd';
import type { Condition, CompareOperator, Dataset, LoopBlock, ReportValue } from '../../types';
import { OPERATOR_LABELS, genId } from '../../types';
import ValueEditor from './value-editor';
import SectionLabel from './section-label';

/** 不需要右值的运算符 */
const NO_RIGHT_OPS: CompareOperator[] = ['IS_NULL', 'IS_NOT_NULL'];

interface ConditionModalProps {
  open: boolean;
  editingCondition: Condition | null;
  datasets: Dataset[];
  loopBlocks?: LoopBlock[];
  onClose: () => void;
  onConfirm: (condition: Condition) => void;
}

/** 条件添加/编辑弹窗 */
const ConditionModal: React.FC<ConditionModalProps> = ({
  open,
  editingCondition,
  datasets,
  loopBlocks = [],
  onClose,
  onConfirm,
}) => {
  const isEdit = editingCondition !== null;

  // 临时状态
  const [left, setLeft] = useState<ReportValue>({ type: 'FieldValue', payload: '' });
  const [operator, setOperator] = useState<CompareOperator>('EQ');
  const [right, setRight] = useState<ReportValue | null>({ type: 'Literal', payload: '' });

  useEffect(() => {
    if (open) {
      if (editingCondition) {
        setLeft(editingCondition.left);
        setOperator(editingCondition.operator);
        setRight(editingCondition.right);
      } else {
        setLeft({ type: 'FieldValue', payload: '' });
        setOperator('EQ');
        setRight({ type: 'Literal', payload: '' });
      }
    }
  }, [open, editingCondition]);

  const hideRight = NO_RIGHT_OPS.includes(operator);

  const handleOperatorChange = (op: CompareOperator) => {
    setOperator(op);
    if (NO_RIGHT_OPS.includes(op)) {
      setRight(null);
    } else if (!right) {
      setRight({ type: 'Literal', payload: '' });
    }
  };

  const handleOk = () => {
    const condition: Condition = {
      id: editingCondition?.id ?? genId(),
      left,
      operator,
      right,
    };
    onConfirm(condition);
  };

  return (
    <Modal
      title={isEdit ? '编辑条件' : '添加条件'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      destroyOnHidden
      width={560}
      okText="确定"
      cancelText="取消"
    >
      <div className="re-form-field">
        <SectionLabel text="左值（字段）" hint="条件判断的左侧值，通常选择数据字段" />
        <ValueEditor
          value={left}
          datasets={datasets}
          loopBlocks={loopBlocks}
          onChange={setLeft}
        />
      </div>

      <div className="re-form-field">
        <SectionLabel text="运算符" hint="比较运算符，如等于、大于、包含等" />
        <Select
          value={operator}
          onChange={handleOperatorChange}
          style={{ width: '100%' }}
          options={Object.entries(OPERATOR_LABELS).map(([v, l]) => ({
            value: v,
            label: l,
          }))}
        />
      </div>

      {!hideRight && (
        <div className="re-form-field">
          <SectionLabel text="右值" hint="条件判断的右侧值，可以是文本、字段或参数" />
          <ValueEditor
            value={right || { type: 'Literal', payload: '' }}
            datasets={datasets}
            loopBlocks={loopBlocks}
            onChange={setRight}
          />
        </div>
      )}
    </Modal>
  );
};

export default ConditionModal;
