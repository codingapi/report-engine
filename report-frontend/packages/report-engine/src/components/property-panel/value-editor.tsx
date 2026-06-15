import React, { useCallback } from 'react';
import { Select, Input, Button } from 'antd';
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import type {
  ReportValue,
  ValueType,
  Aggregation,
  Dataset,
} from '../../types';
import {
  VALUE_TYPE_LABELS,
  AGG_LABELS,
  findDataset,
} from '../../types';
import { templateToString, parseTemplate } from '../../value-text';

interface ValueEditorProps {
  value: ReportValue;
  datasets: Dataset[];
  onChange: (newValue: ReportValue) => void;
  compact?: boolean;
}

// ─── 空值工厂 ────────────────────────────────

function emptyValue(type: ValueType): ReportValue {
  switch (type) {
    case 'Literal':
      return { type: 'Literal', payload: '' };
    case 'FieldValue':
      return { type: 'FieldValue', payload: '' };
    case 'ParamValue':
      return { type: 'ParamValue', payload: '' };
    case 'LoopFieldValue':
      return { type: 'LoopFieldValue', payload: '' };
    case 'NameRef':
      return { type: 'NameRef', payload: '' };
    case 'Template':
      return { type: 'Template', parts: [] };
    case 'Aggregate':
      return { type: 'Aggregate', aggregation: 'SUM', operand: { type: 'FieldValue', payload: '' } };
    case 'FunctionCall':
      return { type: 'FunctionCall', funcName: '', args: [] };
  }
}

// ─── 数据集/字段选项构建 ─────────────────────

function buildDatasetOptions(datasets: Dataset[]) {
  return datasets.map((ds) => ({
    value: ds.id,
    label: ds.alias || ds.id,
  }));
}

function buildFieldOptions(datasets: Dataset[], datasetId: string) {
  const ds = findDataset(datasets, datasetId);
  if (!ds) return [];
  return ds.fields.map((f) => ({
    value: `${ds.id}.${f.name}`,
    label: f.alias || f.name,
  }));
}

// ─── 解析 payload 中的 datasetId ─────────────

function parseFieldRef(payload: string | undefined): { datasetId: string; field: string } {
  if (!payload) return { datasetId: '', field: '' };
  const dot = payload.indexOf('.');
  if (dot === -1) return { datasetId: '', field: '' };
  return { datasetId: payload.slice(0, dot), field: payload.slice(dot + 1) };
}

// ─── 组件 ────────────────────────────────────

const ValueEditor: React.FC<ValueEditorProps> = ({
  value,
  datasets,
  onChange,
  compact = false,
}) => {
  const handleTypeChange = useCallback(
    (newType: ValueType) => {
      onChange(emptyValue(newType));
    },
    [onChange],
  );

  const update = useCallback(
    (patch: Partial<ReportValue>) => {
      onChange({ ...value, ...patch });
    },
    [value, onChange],
  );

  const size = compact ? 'small' : 'small';

  // ─── 类型选择器 ───
  const typeSelector = !compact && (
    <div className="re-prop-value-type">
      <Select
        size={size}
        value={value.type}
        onChange={handleTypeChange}
        style={{ width: '100%' }}
        options={Object.entries(VALUE_TYPE_LABELS).map(([v, l]) => ({
          value: v,
          label: l,
        }))}
      />
    </div>
  );

  // ─── 按类型渲染表单 ───
  // compact 模式下只渲染输入控件（无 label / wrapper），确保在条件行中对齐
  const form = (() => {
    switch (value.type) {
      case 'Literal': {
        const input = (
          <Input.TextArea
            size={size}
            autoSize={compact ? { minRows: 1, maxRows: 1 } : undefined}
            value={value.payload || ''}
            onChange={(e) => update({ payload: e.target.value })}
            placeholder="输入固定文本"
          />
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><label>文本值</label>{input}</div>;
      }

      case 'FieldValue': {
        const { datasetId } = parseFieldRef(value.payload);
        const input = (
          <div className="re-prop-field-cascade">
            <Select
              size={size}
              value={datasetId || undefined}
              onChange={(dsId) => update({ payload: `${dsId}.` })}
              placeholder="数据集"
              options={buildDatasetOptions(datasets)}
              showSearch
            />
            <Select
              size={size}
              value={value.payload || undefined}
              onChange={(ref) => update({ payload: ref })}
              placeholder="字段"
              options={buildFieldOptions(datasets, datasetId)}
              showSearch
              disabled={!datasetId}
            />
          </div>
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><label>数据字段</label>{input}</div>;
      }

      case 'ParamValue': {
        const input = (
          <Input
            size={size}
            value={value.payload || ''}
            onChange={(e) => update({ payload: e.target.value })}
            placeholder="报表参数名称"
          />
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><label>参数名</label>{input}</div>;
      }

      case 'LoopFieldValue': {
        const input = (
          <Input
            size={size}
            value={value.payload || ''}
            onChange={(e) => update({ payload: e.target.value })}
            placeholder="loopId.field"
          />
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><label>循环字段</label>{input}</div>;
      }

      case 'NameRef': {
        const input = (
          <Input
            size={size}
            value={value.payload || ''}
            onChange={(e) => update({ payload: e.target.value })}
            placeholder="运行时名称"
          />
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><label>名称引用</label>{input}</div>;
      }

      case 'Template': {
        const input = (
          <Input.TextArea
            size={size}
            autoSize={compact ? { minRows: 1, maxRows: 1 } : { minRows: 3, maxRows: 6 }}
            value={templateToString(value)}
            onChange={(e) => onChange(parseTemplate(e.target.value))}
            placeholder={'${name}的薪资单 / 合计 ${SUM(d.salary)} 元'}
          />
        );
        if (compact) return input;
        return (
          <div className="re-prop-value-form">
            <label>文本模板</label>
            {input}
            <div style={{ fontSize: 11, color: '#999', lineHeight: 1.5 }}>
              {'用 ${name} 引用运行时名称、${数据集.字段} 引用字段、${SUM(数据集.字段)} 插入聚合'}
            </div>
          </div>
        );
      }

      case 'Aggregate': {
        const input = (
          <div className="re-prop-agg-row">
            <Select
              size={size}
              value={value.aggregation || 'SUM'}
              onChange={(agg: Aggregation) => update({ aggregation: agg })}
              options={Object.entries(AGG_LABELS)
                .filter(([k]) => k !== 'NONE')
                .map(([v, l]) => ({ value: v, label: l }))}
            />
            <ValueEditor
              value={value.operand || { type: 'FieldValue', payload: '' }}
              datasets={datasets}
              onChange={(operand) => update({ operand })}
              compact
            />
          </div>
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><label>聚合方式</label>{input}</div>;
      }

      case 'FunctionCall': {
        const args = value.args || [];
        if (compact) {
          return (
            <div className="re-prop-field-cascade">
              <Input
                size={size}
                value={value.funcName || ''}
                onChange={(e) => update({ funcName: e.target.value })}
                placeholder="函数名"
              />
              <Input
                size={size}
                value={args.map((a) => a.payload || '').join(', ')}
                readOnly
                placeholder="参数"
              />
            </div>
          );
        }
        return (
          <div className="re-prop-value-form">
            <label>函数名</label>
            <Input
              size={size}
              value={value.funcName || ''}
              onChange={(e) => update({ funcName: e.target.value })}
              placeholder="函数名称（如 format、date）"
            />
            <label>
              参数列表
              <Button
                type="link"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => {
                  const newArgs = [...args, { type: 'Literal' as const, payload: '' }];
                  update({ args: newArgs });
                }}
                style={{ float: 'right', padding: 0 }}
              >
                添加
              </Button>
            </label>
            <div className="re-prop-func-args">
              {args.map((arg, i) => (
                <div key={i} className="re-prop-func-arg-row">
                  <ValueEditor
                    value={arg}
                    datasets={datasets}
                    onChange={(newArg) => {
                      const newArgs = [...args];
                      newArgs[i] = newArg;
                      update({ args: newArgs });
                    }}
                    compact
                  />
                  <MinusCircleOutlined
                    style={{ color: '#999', cursor: 'pointer', flexShrink: 0 }}
                    onClick={() => {
                      const newArgs = args.filter((_, j) => j !== i);
                      update({ args: newArgs });
                    }}
                  />
                </div>
              ))}
              {args.length === 0 && (
                <div style={{ fontSize: 12, color: '#bbb' }}>暂无参数</div>
              )}
            </div>
          </div>
        );
      }

      default:
        return <div style={{ fontSize: 12, color: '#999' }}>不支持的类型</div>;
    }
  })();

  return (
    <div>
      {typeSelector}
      {form}
    </div>
  );
};

export default ValueEditor;
