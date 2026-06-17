import React, { useState } from 'react';
import { Button, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Condition, Dataset, LoopBlock, CompareOperator, ReportParam } from '../../types';
import { OPERATOR_LABELS } from '../../types';
import ConditionModal from './condition-modal';

interface ConditionEditorProps {
  conditions: Condition[];
  datasets: Dataset[];
  loopBlocks?: LoopBlock[];
  params?: ReportParam[];
  onChange: (conditions: Condition[]) => void;
}

/** 解析 FieldValue payload (datasetId.fieldName) 为友好显示文本 */
function resolveFieldValue(payload: string | undefined, datasets: Dataset[]): string {
  if (!payload) return '(未选)';
  const [dsId, fieldName] = payload.split('.');
  const ds = datasets.find((d) => d.id === dsId);
  if (!ds) return payload;
  const field = ds.fields?.find((f) => f.name === fieldName);
  const dsLabel = ds.alias || ds.id;
  const fieldLabel = field?.alias || fieldName;
  return `${dsLabel}.${fieldLabel}`;
}

/** 解析参数名为别名 */
function resolveParamName(payload: string | undefined, params: ReportParam[]): string {
  if (!payload) return '(未选)';
  const param = params.find((p) => p.name === payload);
  return param?.alias || payload;
}

/** 简短描述一个条件值 */
function describeValue(cond: Condition, datasets: Dataset[], params: ReportParam[]): { left: string; op: string; right: string } {
  const describeOne = (v: Condition['left'] | null | undefined): string => {
    if (!v) return '';
    switch (v.type) {
      case 'FieldValue':
        return resolveFieldValue(v.payload, datasets);
      case 'LoopFieldValue':
        return `循环.${v.payload}`;
      case 'ParamValue':
        return `参数:${resolveParamName(v.payload, params)}`;
      default:
        return String(v.payload ?? '');
    }
  };

  return {
    left: describeOne(cond.left),
    op: OPERATOR_LABELS[cond.operator as CompareOperator] || cond.operator,
    right: describeOne(cond.right),
  };
}

const ConditionEditor: React.FC<ConditionEditorProps> = ({
  conditions,
  datasets,
  loopBlocks = [],
  params = [],
  onChange,
}) => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCondition, setEditingCondition] = useState<Condition | null>(null);

  const openAdd = () => {
    setEditingCondition(null);
    setModalOpen(true);
  };

  const openEdit = (cond: Condition) => {
    setEditingCondition(cond);
    setModalOpen(true);
  };

  const handleConfirm = (cond: Condition) => {
    if (editingCondition) {
      onChange(conditions.map((c) => (c.id === editingCondition.id ? cond : c)));
    } else {
      onChange([...conditions, cond]);
    }
    setModalOpen(false);
    setEditingCondition(null);
  };

  const handleDelete = (id: string) => {
    onChange(conditions.filter((c) => c.id !== id));
  };

  return (
    <div>
      {conditions.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {conditions.map((cond) => {
            const { left, op, right } = describeValue(cond, datasets, params);
            return (
              <div
                key={cond.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '6px 8px',
                  background: '#fafafa',
                  borderRadius: 4,
                  fontSize: 12,
                }}
              >
                <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  <Tag color="blue" style={{ marginRight: 4 }}>{left}</Tag>
                  <span style={{ color: '#666' }}>{op}</span>
                  {right && <Tag color="green" style={{ marginLeft: 4 }}>{right}</Tag>}
                </span>
                <Button
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEdit(cond)}
                />
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => handleDelete(cond.id)}
                />
              </div>
            );
          })}
        </div>
      ) : (
        <div className="re-prop-cond-empty">暂无过滤条件</div>
      )}

      <Button
        type="dashed"
        size="small"
        icon={<PlusOutlined />}
        onClick={openAdd}
        block
        style={{ marginTop: conditions.length > 0 ? 8 : 0 }}
      >
        添加条件
      </Button>

      <ConditionModal
        open={modalOpen}
        editingCondition={editingCondition}
        datasets={datasets}
        loopBlocks={loopBlocks}
        onClose={() => {
          setModalOpen(false);
          setEditingCondition(null);
        }}
        onConfirm={handleConfirm}
      />
    </div>
  );
};

export default ConditionEditor;
