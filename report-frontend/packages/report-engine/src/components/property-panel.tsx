import React from 'react';
import { Empty, Tag } from 'antd';
import type { CellBinding, Dataset } from '../types';
import { VALUE_TYPE_LABELS, EXPANSION_LABELS, AGG_LABELS, findField } from '../types';
import type { SheetCellSelectInfo } from './sheet-panel';

interface PropertyPanelProps {
  selectedCell: SheetCellSelectInfo | null;
  cellBindings: CellBinding[];
  datasets: Dataset[];
}

/**
 * 右面板：显示选中单元格的绑定信息（当前只读展示，后续增加编辑能力）。
 */
const PropertyPanel: React.FC<PropertyPanelProps> = ({
  selectedCell,
  cellBindings,
  datasets,
}) => {
  if (!selectedCell) {
    return (
      <div className="re-panel">
        <div className="re-panel__title">属性设置</div>
        <div className="re-panel__content">
          <div className="re-prop-empty">选择一个单元格查看属性</div>
        </div>
      </div>
    );
  }

  const { info } = selectedCell;
  const cellKey = `${info.sheetId}:${info.row}:${info.column}`;
  const binding = cellBindings.find((b) => b.cellKey === cellKey);

  return (
    <div className="re-panel">
      <div className="re-panel__title">属性设置</div>
      <div className="re-panel__content">
        <div className="re-prop-panel">
          {/* 单元格信息 */}
          <div className="re-prop-cell-info">
            <div>
              <span style={{ color: '#999' }}>位置：</span>
              {info.a1Notation || `${info.row},${info.column}`}
            </div>
            {info.value != null && (
              <div style={{ marginTop: 4, color: '#666' }}>
                值：{String(info.value).slice(0, 50)}
              </div>
            )}
          </div>

          {binding ? (
            <>
              {/* 值表达式 */}
              <div className="re-prop-section">
                <div className="re-prop-section__title">值表达式</div>
                <div className="re-prop-value-display">
                  <Tag color="blue">{VALUE_TYPE_LABELS[binding.value.type]}</Tag>
                  <span style={{ marginLeft: 4 }}>
                    {describeValue(binding.value, datasets)}
                  </span>
                </div>
              </div>

              {/* 扩展设置 */}
              <div className="re-prop-section">
                <div className="re-prop-section__title">扩展</div>
                <div style={{ fontSize: 13, color: '#666' }}>
                  {EXPANSION_LABELS[binding.expansion]}
                  {binding.expansion !== 'NONE' && (
                    <Tag style={{ marginLeft: 8 }} color={binding.expandMode === 'GROUP' ? 'orange' : 'green'}>
                      {binding.expandMode}
                    </Tag>
                  )}
                </div>
                {binding.mergeRepeated && (
                  <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
                    合并重复值
                  </div>
                )}
                {binding.parentCell && (
                  <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
                    父格：{binding.parentCell}
                  </div>
                )}
              </div>

              {/* 条件 */}
              {binding.conditions.length > 0 && (
                <div className="re-prop-section">
                  <div className="re-prop-section__title">
                    过滤条件 ({binding.conditions.length})
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="re-prop-section">
              <div style={{ fontSize: 13, color: '#999' }}>
                未绑定数据。从左侧数据集拖拽字段到此单元格。
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

/** 将 ReportValue 描述为可读文本 */
function describeValue(
  value: { type: string; payload?: string; aggregation?: string; operand?: { type: string; payload?: string } },
  datasets: Dataset[],
): string {
  switch (value.type) {
    case 'FieldValue': {
      const ref = value.payload || '';
      const field = findField(datasets, ref);
      return field ? `${field.alias} (${ref})` : ref;
    }
    case 'Aggregate': {
      const agg = value.aggregation || '';
      const operand = value.operand ? describeValue(value.operand, datasets) : '';
      return `${AGG_LABELS[agg as keyof typeof AGG_LABELS] || agg}(${operand})`;
    }
    case 'Literal':
      return `"${value.payload || ''}"`;
    case 'NameRef':
    case 'ParamValue':
      return value.payload || '';
    default:
      return value.payload || value.type;
  }
}

export default PropertyPanel;
