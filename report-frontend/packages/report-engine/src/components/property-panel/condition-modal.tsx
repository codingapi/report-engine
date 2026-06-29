import { useEffect, useMemo, useState } from 'react';
import { Modal, Form, Select } from 'antd';
import type {
  Condition,
  CompareOperator,
  DataType,
  Dataset,
  LoopBlock,
  ParamDTO,
  ReportValue,
} from '@/types';
import { OPERATOR_LABELS, genId, findField, findDataset, operatorsForDataType } from '@/types';
import ValueEditor from './value-editor';

/** 不需要右值的运算符 */
const NO_RIGHT_OPS: CompareOperator[] = ['IS_NULL', 'IS_NOT_NULL'];

/**
 * 解析左值表达式的数据类型，用于按类型过滤可用算子。
 * - FieldValue（datasetId.field）：查字段 dataType
 * - LoopFieldValue（loopId.field）：查循环驱动数据集字段 dataType
 * - ParamValue：查参数 dataType
 * - 其它（Literal/Aggregate/…）：未知 → undefined（放开全部算子）
 */
function resolveLeftDataType(
  value: ReportValue,
  datasets: Dataset[],
  loopBlocks: LoopBlock[],
  params: ParamDTO[],
): DataType | undefined {
  if (value.type === 'FieldValue') {
    return findField(datasets, value.payload ?? '')?.dataType;
  }
  if (value.type === 'LoopFieldValue') {
    const ref = value.payload ?? '';
    const dot = ref.indexOf('.');
    if (dot === -1) return undefined;
    const loop = loopBlocks.find((l) => l.id === ref.slice(0, dot));
    if (!loop) return undefined;
    const ds = findDataset(datasets, loop.source.datasetId);
    return ds?.fields.find((f) => f.name === ref.slice(dot + 1))?.dataType;
  }
  if (value.type === 'ParamValue') {
    return params.find((p) => p.name === value.payload)?.dataType;
  }
  return undefined;
}

interface ConditionModalProps {
  open: boolean;
  editingCondition: Condition | null;
  datasets: Dataset[];
  loopBlocks?: LoopBlock[];
  params?: ParamDTO[];
  onClose: () => void;
  onConfirm: (condition: Condition) => void;
}

/** 条件添加/编辑弹窗 */
const ConditionModal: React.FC<ConditionModalProps> = ({
  open,
  editingCondition,
  datasets,
  loopBlocks = [],
  params = [],
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

  // 按左值字段类型过滤可用算子（类型未知则放开全部）
  const availableOps = useMemo(
    () => operatorsForDataType(resolveLeftDataType(left, datasets, loopBlocks, params)),
    [left, datasets, loopBlocks, params],
  );

  // 左值类型变化导致当前算子不再可用时，回退到第一个可用算子（默认 EQ）
  useEffect(() => {
    if (!availableOps.includes(operator)) {
      const next = availableOps[0] ?? 'EQ';
      setOperator(next);
      if (NO_RIGHT_OPS.includes(next)) setRight(null);
      else setRight((r) => r ?? { type: 'Literal', payload: '' });
    }
  }, [availableOps, operator]);

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
    >
      <Form layout="vertical" size="small">
        <Form.Item label="左值（字段）" tooltip="条件判断的左侧值，通常选择数据字段">
          <ValueEditor
            value={left}
            datasets={datasets}
            loopBlocks={loopBlocks}
            params={params}
            onChange={setLeft}
            bare
            types={['FieldValue', 'LoopFieldValue', 'ParamValue', 'Literal', 'Aggregate']}
          />
        </Form.Item>

        <Form.Item label="运算符" tooltip="比较运算符，如等于、大于、包含等">
          <Select
            value={operator}
            onChange={handleOperatorChange}
            options={availableOps.map((op) => ({
              value: op,
              label: OPERATOR_LABELS[op],
            }))}
          />
        </Form.Item>

        {!hideRight && (
          <Form.Item
            label="右值"
            tooltip={
              operator === 'BETWEEN'
                ? '范围比较：输入 小值,大值，如 1,100'
                : '条件判断的右侧值，可以是文本、字段或参数'
            }
          >
            <ValueEditor
              value={right || { type: 'Literal', payload: '' }}
              datasets={datasets}
              loopBlocks={loopBlocks}
              params={params}
              onChange={setRight}
              bare
              types={['FieldValue', 'LoopFieldValue', 'ParamValue', 'Literal', 'Aggregate']}
            />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
};

export default ConditionModal;
