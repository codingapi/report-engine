import React, { useMemo } from 'react';
import { Select, Input, Button } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { DataConfig, DataType } from '../datasource/types';
import {
  CompareOperator,
  OPERATOR_LABELS,
  OPERATORS_BY_TYPE,
  NO_VALUE_OPERATORS,
  type ConditionRule,
} from './types';

interface ConditionRowProps {
  condition: ConditionRule;
  dataConfig: DataConfig;
  onChange: (updated: ConditionRule) => void;
  onRemove: () => void;
}

/** 从 "tableName.fieldName" 查找字段数据类型 */
const findFieldDataType = (field: string, dataConfig: DataConfig): DataType | null => {
  const dotIndex = field.indexOf('.');
  if (dotIndex === -1) return null;
  const tableName = field.slice(0, dotIndex);
  const fieldName = field.slice(dotIndex + 1);
  const table = dataConfig.tables.find((t) => t.name === tableName);
  return table?.fields.find((f) => f.name === fieldName)?.dataType ?? null;
};

const ConditionRow: React.FC<ConditionRowProps> = ({ condition, dataConfig, onChange, onRemove }) => {
  /** 字段选项：按表分组 */
  const fieldOptions = useMemo(() => {
    return dataConfig.tables.map((table) => ({
      label: table.alias || table.name,
      options: table.fields.map((field) => ({
        label: `${field.alias || field.name}`,
        value: `${table.name}.${field.name}`,
      })),
    }));
  }, [dataConfig]);

  /** 运算符选项：根据选中字段的 DataType 过滤 */
  const operatorOptions = useMemo(() => {
    const dataType = findFieldDataType(condition.field, dataConfig);
    const allowedOps = dataType
      ? OPERATORS_BY_TYPE[dataType]
      : Object.values(CompareOperator);
    return allowedOps.map((op) => ({
      label: OPERATOR_LABELS[op],
      value: op,
    }));
  }, [condition.field, dataConfig]);

  const showValueInput = !NO_VALUE_OPERATORS.has(condition.operator);

  return (
    <div className="report-engine__property-condition-row">
      <Select
        placeholder="选择字段"
        value={condition.field || undefined}
        options={fieldOptions}
        onChange={(field) => {
          // 切换字段时重置运算符和值
          onChange({ ...condition, field, operator: CompareOperator.EQUALS, value: '' });
        }}
        showSearch
        optionFilterProp="label"
        size="small"
        style={{ flex: '2', minWidth: 0 }}
      />
      <Select
        placeholder="运算符"
        value={condition.operator || undefined}
        options={operatorOptions}
        onChange={(operator) => onChange({ ...condition, operator, value: '' })}
        size="small"
        style={{ flex: '1.5', minWidth: 0 }}
      />
      {showValueInput && (
        <Input
          placeholder="值"
          value={condition.value}
          onChange={(e) => onChange({ ...condition, value: e.target.value })}
          size="small"
          style={{ flex: '1', minWidth: 0 }}
        />
      )}
      <Button
        type="text"
        size="small"
        danger
        icon={<DeleteOutlined />}
        onClick={onRemove}
      />
    </div>
  );
};

export default ConditionRow;
