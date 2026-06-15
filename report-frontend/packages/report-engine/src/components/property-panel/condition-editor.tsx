import React from 'react';
import { Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { Condition, Dataset } from '../../types';
import { genId } from '../../types';
import ConditionRow from './condition-row';

interface ConditionEditorProps {
  conditions: Condition[];
  datasets: Dataset[];
  onChange: (conditions: Condition[]) => void;
}

const ConditionEditor: React.FC<ConditionEditorProps> = ({
  conditions,
  datasets,
  onChange,
}) => {
  const handleAdd = () => {
    const newCondition: Condition = {
      id: genId(),
      left: { type: 'FieldValue', payload: '' },
      operator: 'EQ',
      right: { type: 'Literal', payload: '' },
    };
    onChange([...conditions, newCondition]);
  };

  const handleChange = (index: number, updated: Condition) => {
    const next = [...conditions];
    next[index] = updated;
    onChange(next);
  };

  const handleDelete = (index: number) => {
    onChange(conditions.filter((_, i) => i !== index));
  };

  return (
    <div>
      {conditions.length > 0 ? (
        <div className="re-prop-cond-list">
          {conditions.map((cond, i) => (
            <ConditionRow
              key={cond.id}
              condition={cond}
              datasets={datasets}
              onChange={(updated) => handleChange(i, updated)}
              onDelete={() => handleDelete(i)}
            />
          ))}
        </div>
      ) : (
        <div className="re-prop-cond-empty">暂无过滤条件</div>
      )}

      <Button
        type="dashed"
        size="small"
        icon={<PlusOutlined />}
        onClick={handleAdd}
        block
        style={{ marginTop: conditions.length > 0 ? 8 : 0 }}
      >
        添加条件
      </Button>
    </div>
  );
};

export default ConditionEditor;
