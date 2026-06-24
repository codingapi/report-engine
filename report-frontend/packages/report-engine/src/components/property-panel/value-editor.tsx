import React, { useCallback } from 'react';
import { Select, Input, Button, Empty, Typography, Form, Space } from 'antd';
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import type { ReportValue, ValueType, Aggregation, Dataset, LoopBlock } from '@/types';
import { VALUE_TYPE_LABELS, AGG_LABELS, findDataset } from '@/types';
import { templateToString, parseTemplate } from '@/value-text';
import { datasetOptions, fieldOptions } from '@/utils/dataset-options';

interface ValueEditorProps {
  value: ReportValue;
  datasets: Dataset[];
  /** 当前 sheet 的循环块（用于循环字段级联：选循环块 → 选其数据集字段） */
  loopBlocks?: LoopBlock[];
  onChange: (newValue: ReportValue) => void;
  compact?: boolean;
  /** 裸模式：不渲染内层 Form.Item / label，用于已在外层 Form.Item 包裹的场景（如条件弹窗） */
  bare?: boolean;
  /** 限制可选的值类型（用于条件场景等需要限制类型选择的场景） */
  types?: ValueType[];
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
      return {
        type: 'Aggregate',
        aggregation: 'SUM',
        operand: { type: 'FieldValue', payload: '' },
      };
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

// ─── 组件 ────────────────────────────────────

const ValueEditor: React.FC<ValueEditorProps> = ({
  value,
  datasets,
  loopBlocks = [],
  onChange,
  compact = false,
  bare = false,
  types,
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
    <div className="re-prop-value-type" style={bare ? { marginBottom: 4 } : undefined}>
      <Select
        size={size}
        value={value.type}
        onChange={handleTypeChange}
        style={{ width: '100%' }}
        options={Object.entries(VALUE_TYPE_LABELS)
          .filter(([v]) => !types || types.includes(v as ValueType))
          .map(([v, l]) => ({
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
        if (compact || bare) return input;
        return <Form.Item label="文本值">{input}</Form.Item>;
      }

      case 'FieldValue': {
        const { datasetId } = parseFieldRef(value.payload);
        const input = (
          <Space.Compact style={{ width: '100%' }}>
            <Select
              size={size}
              value={datasetId || undefined}
              onChange={(dsId) => update({ payload: `${dsId}.` })}
              placeholder="数据集"
              options={datasetOptions(datasets)}
              showSearch
              style={{ flex: 1, minWidth: 0 }}
            />
            <Select
              size={size}
              value={value.payload || undefined}
              onChange={(ref) => update({ payload: ref })}
              placeholder="字段"
              options={fieldOptions(datasets, datasetId, true)}
              showSearch
              disabled={!datasetId}
              style={{ flex: 1, minWidth: 0 }}
            />
          </Space.Compact>
        );
        if (compact || bare) return input;
        return <Form.Item label="数据字段">{input}</Form.Item>;
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
        if (compact || bare) return input;
        return <Form.Item label="参数名">{input}</Form.Item>;
      }

      case 'LoopFieldValue': {
        // loopId.field —— 选循环块 → 选其驱动数据集的字段
        const { datasetId: loopId } = parseFieldRef(value.payload);
        const loop = loopBlocks.find((l) => l.id === loopId);
        const loopDs = loop ? findDataset(datasets, loop.source.datasetId) : null;
        const input = (
          <Space.Compact style={{ width: '100%' }}>
            <Select
              size={size}
              value={loopId || undefined}
              onChange={(id) => update({ payload: `${id}.` })}
              placeholder="循环块"
              options={loopBlocks.map((l) => ({ value: l.id, label: l.label || l.id }))}
              showSearch
              notFoundContent="无循环块（请在表格选区右键创建）"
              style={{ flex: 1, minWidth: 0 }}
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
              style={{ flex: 1, minWidth: 0 }}
            />
          </Space.Compact>
        );
        if (compact || bare) return input;
        return <Form.Item label="循环字段">{input}</Form.Item>;
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
        if (compact || bare) return input;
        return <Form.Item label="名称引用">{input}</Form.Item>;
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
        if (compact || bare) return input;
        return (
          <Form.Item
            label="文本模板"
            extra={
              '用 ${name} 引用运行时名称、${数据集.字段} 引用字段、${SUM(数据集.字段)} 插入聚合'
            }
          >
            {input}
          </Form.Item>
        );
      }

      case 'Aggregate': {
        // compact/bare 模式：扁平化渲染三个 Select，避免嵌套 Space.Compact 导致宽度问题
        if (compact || bare) {
          const operand = value.operand || { type: 'FieldValue', payload: '' };
          const operandType = operand.type;

          // 如果是 FieldValue 类型，直接渲染数据集和字段选择器
          if (operandType === 'FieldValue') {
            const { datasetId } = parseFieldRef(operand.payload);
            return (
              <Space.Compact style={{ width: '100%' }}>
                <Select
                  size={size}
                  value={value.aggregation || 'SUM'}
                  onChange={(agg: Aggregation) => update({ aggregation: agg })}
                  options={Object.entries(AGG_LABELS)
                    .filter(([k]) => k !== 'NONE')
                    .map(([v, l]) => ({ value: v, label: l }))}
                  style={{ flex: 1, minWidth: 0 }}
                />
                <Select
                  size={size}
                  value={datasetId || undefined}
                  onChange={(dsId) =>
                    update({ operand: { type: 'FieldValue', payload: `${dsId}.` } })
                  }
                  placeholder="数据集"
                  options={datasetOptions(datasets)}
                  showSearch
                  style={{ flex: 1, minWidth: 0 }}
                />
                <Select
                  size={size}
                  value={operand.payload || undefined}
                  onChange={(ref) => update({ operand: { type: 'FieldValue', payload: ref } })}
                  placeholder="字段"
                  options={fieldOptions(datasets, datasetId, true)}
                  showSearch
                  disabled={!datasetId}
                  style={{ flex: 1, minWidth: 0 }}
                />
              </Space.Compact>
            );
          }

          // 其他类型的操作数，回退到递归渲染
          return (
            <Space.Compact style={{ width: '100%' }}>
              <Select
                size={size}
                value={value.aggregation || 'SUM'}
                onChange={(agg: Aggregation) => update({ aggregation: agg })}
                options={Object.entries(AGG_LABELS)
                  .filter(([k]) => k !== 'NONE')
                  .map(([v, l]) => ({ value: v, label: l }))}
                style={{ flex: 1, minWidth: 0 }}
              />
              <ValueEditor
                value={operand}
                datasets={datasets}
                loopBlocks={loopBlocks}
                onChange={(newOperand) => update({ operand: newOperand })}
                compact
              />
            </Space.Compact>
          );
        }

        // 正常模式：使用 Form.Item 包装
        const input = (
          <Space.Compact style={{ width: '100%' }}>
            <Select
              size={size}
              value={value.aggregation || 'SUM'}
              onChange={(agg: Aggregation) => update({ aggregation: agg })}
              options={Object.entries(AGG_LABELS)
                .filter(([k]) => k !== 'NONE')
                .map(([v, l]) => ({ value: v, label: l }))}
              style={{ width: '40%' }}
            />
            <ValueEditor
              value={value.operand || { type: 'FieldValue', payload: '' }}
              datasets={datasets}
              loopBlocks={loopBlocks}
              onChange={(operand) => update({ operand })}
              compact
            />
          </Space.Compact>
        );
        return <Form.Item label="聚合方式">{input}</Form.Item>;
      }

      case 'FunctionCall': {
        const args = value.args || [];
        if (compact) {
          return (
            <Space.Compact style={{ width: '100%' }}>
              <Input
                size={size}
                value={value.funcName || ''}
                onChange={(e) => update({ funcName: e.target.value })}
                placeholder="函数名"
                style={{ flex: 1, minWidth: 0 }}
              />
              <Input
                size={size}
                value={args.map((a) => a.payload || '').join(', ')}
                readOnly
                placeholder="参数"
                style={{ flex: 1, minWidth: 0 }}
              />
            </Space.Compact>
          );
        }
        if (bare) {
          return (
            <>
              <Input
                size={size}
                value={value.funcName || ''}
                onChange={(e) => update({ funcName: e.target.value })}
                placeholder="函数名称（如 format、date）"
                style={{ marginBottom: 4 }}
              />
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
                  添加参数
                </Button>
              </div>
            </>
          );
        }
        return (
          <>
            <Form.Item label="函数名">
              <Input
                size={size}
                value={value.funcName || ''}
                onChange={(e) => update({ funcName: e.target.value })}
                placeholder="函数名称（如 format、date）"
              />
            </Form.Item>
            <Form.Item
              label="参数列表"
              extra={
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
              }
            >
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
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无参数"
                    style={{ margin: '8px 0' }}
                  />
                )}
              </div>
            </Form.Item>
          </>
        );
      }

      default:
        return (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            不支持的类型
          </Typography.Text>
        );
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
