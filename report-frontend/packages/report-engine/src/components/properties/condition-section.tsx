import React from 'react';
import { Button, Empty } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { DataConfig } from '../datasource/types';
import {
  CompareOperator,
  type ConditionRule,
  type ConditionAxis,
  generateId,
} from './types';
import ConditionRow from './condition-row';

interface ConditionSectionProps {
  title: string;
  axis: ConditionAxis;
  conditions: ConditionRule[];
  dataConfig: DataConfig;
  onChange: (conditions: ConditionRule[]) => void;
}

const ConditionSection: React.FC<ConditionSectionProps> = ({
  title,
  conditions,
  dataConfig,
  onChange,
}) => {
  const handleAdd = () => {
    const newCondition: ConditionRule = {
      id: generateId(),
      field: '',
      operator: CompareOperator.EQUALS,
      value: '',
    };
    onChange([...conditions, newCondition]);
  };

  const handleUpdate = (index: number, updated: ConditionRule) => {
    const next = [...conditions];
    next[index] = updated;
    onChange(next);
  };

  const handleRemove = (index: number) => {
    onChange(conditions.filter((_, i) => i !== index));
  };

  return (
    <div className="report-engine__property-section">
      <div className="report-engine__property-section-title">
        <span>{title}</span>
      </div>

      {conditions.length === 0 ? (
        <div className="report-engine__property-section-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无条件"
            style={{ margin: '8px 0' }}
          />
        </div>
      ) : (
        conditions.map((cond, index) => (
          <ConditionRow
            key={cond.id}
            condition={cond}
            dataConfig={dataConfig}
            onChange={(updated) => handleUpdate(index, updated)}
            onRemove={() => handleRemove(index)}
          />
        ))
      )}

      <Button
        type="dashed"
        size="small"
        icon={<PlusOutlined />}
        onClick={handleAdd}
        block
      >
        添加条件
      </Button>
    </div>
  );
};

export default ConditionSection;
