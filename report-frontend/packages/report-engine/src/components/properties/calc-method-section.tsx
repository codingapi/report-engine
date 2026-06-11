import React, { useMemo } from 'react';
import { Select } from 'antd';
import type { DataConfig, DataType } from '../datasource/types';
import {
  CalcMethod,
  CALC_METHOD_LABELS,
  CALC_METHODS_BY_TYPE,
} from './types';

interface CalcMethodSectionProps {
  value: CalcMethod | null;
  /** 当前选中字段 "tableName.fieldName"，用于过滤可用计算方式 */
  selectedField: string | null;
  dataConfig: DataConfig;
  onChange: (method: CalcMethod | null) => void;
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

const CalcMethodSection: React.FC<CalcMethodSectionProps> = ({
  value,
  selectedField,
  dataConfig,
  onChange,
}) => {
  const options = useMemo(() => {
    if (!selectedField) {
      // 未选字段时显示所有计算方式
      return Object.values(CalcMethod).map((m) => ({
        label: CALC_METHOD_LABELS[m],
        value: m,
      }));
    }
    const dataType = findFieldDataType(selectedField, dataConfig);
    if (!dataType) {
      return Object.values(CalcMethod).map((m) => ({
        label: CALC_METHOD_LABELS[m],
        value: m,
      }));
    }
    return CALC_METHODS_BY_TYPE[dataType].map((m) => ({
      label: CALC_METHOD_LABELS[m],
      value: m,
    }));
  }, [selectedField, dataConfig]);

  return (
    <div className="report-engine__property-section">
      <div className="report-engine__property-section-title">
        <span>计算方式</span>
      </div>
      <Select
        placeholder="选择计算方式"
        value={value ?? undefined}
        options={options}
        onChange={(val) => onChange(val ?? null)}
        allowClear
        size="small"
        style={{ width: '100%' }}
      />
      {!selectedField && (
        <div className="report-engine__property-section-hint">
          选择条件字段后可按数据类型过滤计算方式
        </div>
      )}
    </div>
  );
};

export default CalcMethodSection;
