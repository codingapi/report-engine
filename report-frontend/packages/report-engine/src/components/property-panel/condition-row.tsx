import React from 'react';
import { Select, Button } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { Condition, CompareOperator, Dataset, LoopBlock } from '../../types';
import { OPERATOR_LABELS } from '../../types';
import ValueEditor from './value-editor';

interface ConditionRowProps {
  condition: Condition;
  datasets: Dataset[];
  loopBlocks?: LoopBlock[];
  onChange: (updated: Condition) => void;
  onDelete: () => void;
}

/** 不需要右值的运算符 */
const NO_RIGHT_OPS: CompareOperator[] = ['IS_NULL', 'IS_NOT_NULL'];

const ConditionRow: React.FC<ConditionRowProps> = ({
  condition,
  datasets,
  loopBlocks = [],
  onChange,
  onDelete,
}) => {
  const hideRight = NO_RIGHT_OPS.includes(condition.operator);

  return (
    <div className="re-prop-cond-row">
      <div className="re-prop-cond-left">
        <ValueEditor
          value={condition.left}
          datasets={datasets}
          loopBlocks={loopBlocks}
          onChange={(left) => onChange({ ...condition, left })}
          compact
        />
      </div>

      <div className="re-prop-cond-op">
        <Select
          size="small"
          value={condition.operator}
          onChange={(op: CompareOperator) => {
            const hideRightNow = NO_RIGHT_OPS.includes(op);
            onChange({
              ...condition,
              operator: op,
              right: hideRightNow ? null : condition.right,
            });
          }}
          style={{ width: '100%' }}
          options={Object.entries(OPERATOR_LABELS).map(([v, l]) => ({
            value: v,
            label: l,
          }))}
        />
      </div>

      {!hideRight && (
        <div className="re-prop-cond-right">
          <ValueEditor
            value={condition.right || { type: 'Literal', payload: '' }}
            datasets={datasets}
            loopBlocks={loopBlocks}
            onChange={(right) => onChange({ ...condition, right })}
            compact
          />
        </div>
      )}

      <div className="re-prop-cond-del">
        <Button
          type="text"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={onDelete}
        />
      </div>
    </div>
  );
};

export default ConditionRow;
