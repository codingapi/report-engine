import React, { useCallback } from 'react';
import { Select, Input, Button, Empty, Typography } from 'antd';
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import type {
  ReportValue,
  ValueType,
  Aggregation,
  Dataset,
  LoopBlock,
} from '../../types';
import {
  VALUE_TYPE_LABELS,
  AGG_LABELS,
  findDataset,
} from '../../types';
import { templateToString, parseTemplate } from '../../value-text';
import { datasetOptions, fieldOptions } from '../../utils/dataset-options';

interface ValueEditorProps {
  value: ReportValue;
  datasets: Dataset[];
  /** 当前 sheet 的循环块（用于循环字段级联：选循环块 → 选其数据集字段） */
  loopBlocks?: LoopBlock[];
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

// ─── 解析 payload 中的 datasetId ─────────────

function parseFieldRef(payload: string | undefined): { datasetId: string; field: string } {
  if (!payload) return { datasetId: '', field: '' };
  const dot = payload.indexOf('.');
  if (dot === -1) return { datasetId: '', field: '' };
  return { datasetId: payload.slice(0, dot), field: payload.slice(dot + 1) };
}

/** 字段标签（替代 raw <label>） */
const FieldLabel: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <Typography.Text style={{ fontSize: 12, fontWeight: 500 }}>
    {children}
  </Typography.Text>
);

// ─── 组件 ────────────────────────────────────

const ValueEditor: React.FC<ValueEditorProps> = ({
  value,
  datasets,
  loopBlocks = [],
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
        return <div className="re-prop-value-form"><FieldLabel>文本值</FieldLabel>{input}</div>;
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
              options={datasetOptions(datasets)}
              showSearch
            />
            <Select
              size={size}
              value={value.payload || undefined}
              onChange={(ref) => update({ payload: ref })}
              placeholder="字段"
              options={fieldOptions(datasets, datasetId, true)}
              showSearch
              disabled={!datasetId}
            />
          </div>
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><FieldLabel>数据字段</FieldLabel>{input}</div>;
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
        return <div className="re-prop-value-form"><FieldLabel>参数名</FieldLabel>{input}</div>;
      }

      case 'LoopFieldValue': {
        // loopId.field —— 选循环块 → 选其驱动数据集的字段
        const { datasetId: loopId } = parseFieldRef(value.payload);
        const loop = loopBlocks.find((l) => l.id === loopId);
        const loopDs = loop ? findDataset(datasets, loop.source.datasetId) : null;
        const input = (
          <div className="re-prop-field-cascade">
            <Select
              size={size}
              value={loopId || undefined}
              onChange={(id) => update({ payload: `${id}.` })}
              placeholder="循环块"
              options={loopBlocks.map((l) => ({ value: l.id, label: l.label || l.id }))}
              showSearch
              notFoundContent="无循环块（请在表格选区右键创建）"
            />
            <Select
              size={size}
              value={value.payload || undefined}
              onChange={(ref) => update({ payload: ref })}
              placeholder="字段"
              disabled={!loopId}
              options={(loopDs?.fields || []).map((f) => ({
                value: `${loopId}.${f.name}`,
                label: f.alias || f.name,
              }))}
              showSearch
            />
          </div>
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><FieldLabel>循环字段</FieldLabel>{input}</div>;
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
        return <div className="re-prop-value-form"><FieldLabel>名称引用</FieldLabel>{input}</div>;
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
            <FieldLabel>文本模板</FieldLabel>
            {input}
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {'用 ${name} 引用运行时名称、${数据集.字段} 引用字段、${SUM(数据集.字段)} 插入聚合'}
            </Typography.Text>
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
              loopBlocks={loopBlocks}
              onChange={(operand) => update({ operand })}
              compact
            />
          </div>
        );
        if (compact) return input;
        return <div className="re-prop-value-form"><FieldLabel>聚合方式</FieldLabel>{input}</div>;
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
            <FieldLabel>函数名</FieldLabel>
            <Input
              size={size}
              value={value.funcName || ''}
              onChange={(e) => update({ funcName: e.target.value })}
              placeholder="函数名称（如 format、date）"
            />
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <FieldLabel>参数列表</FieldLabel>
              <Button
                type="link"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => {
                  const newArgs = [...args, { type: 'Literal' as const, payload: '' }];
                  update({ args: newArgs });
                }}
                style={{ padding: 0 }}
              >
                添加
              </Button>
            </div>
            <div className="re-prop-func-args">
              {args.map((arg, i) => (
                <div key={i} className="re-prop-func-arg-row">
                  <ValueEditor
                    value={arg}
                    datasets={datasets}
                    loopBlocks={loopBlocks}
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
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无参数" style={{ margin: '8px 0' }} />
              )}
            </div>
          </div>
        );
      }

      default:
        return <Typography.Text type="secondary" style={{ fontSize: 12 }}>不支持的类型</Typography.Text>;
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
