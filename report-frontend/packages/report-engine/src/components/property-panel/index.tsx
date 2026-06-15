import React from 'react';
import { Tabs, Button, Popconfirm, Badge, Space } from 'antd';
import { PlusOutlined, DeleteOutlined, TableOutlined } from '@ant-design/icons';
import type { CellBinding, SummaryRow, LoopBlock, Dataset } from '../../types';
import type { SheetCellSelectInfo } from '../sheet-panel';
import ValueEditor from './value-editor';
import ExpansionEditor from './expansion-editor';
import ConditionEditor from './condition-editor';
import SummaryRowEditor from './summary-row-editor';
import { valueDisplayText, summaryCellText } from '../../value-text';

interface PropertyPanelProps {
  selectedCell: SheetCellSelectInfo | null;
  cellBindings: CellBinding[];
  summaries: SummaryRow[];
  loopBlocks: LoopBlock[];
  datasets: Dataset[];
  onBindingChange: (cellKey: string, binding: CellBinding) => void;
  onBindingCreate: (cellKey: string) => void;
  onBindingDelete: (cellKey: string) => void;
  onSummaryRowChange: (id: string, row: SummaryRow) => void;
  onSummaryRowCreate: (row: number) => void;
  onSummaryRowDelete: (id: string) => void;
}

const PropertyPanel: React.FC<PropertyPanelProps> = ({
  selectedCell,
  cellBindings,
  summaries,
  loopBlocks,
  datasets,
  onBindingChange,
  onBindingCreate,
  onBindingDelete,
  onSummaryRowChange,
  onSummaryRowCreate,
  onSummaryRowDelete,
}) => {
  if (!selectedCell) {
    return (
      <div className="re-panel">
        <div className="re-panel__title" style={{ paddingLeft: 32 }}>属性面板</div>
        <div className="re-panel__content">
          <div className="re-prop-empty">选择一个单元格查看属性</div>
        </div>
      </div>
    );
  }

  const { info } = selectedCell;
  const cellKey = `${info.sheetId}:${info.row}:${info.column}`;
  const binding = cellBindings.find((b) => b.cellKey === cellKey);
  // 选中行是否为汇总行（设计态按行号锚定）
  const summaryRow = summaries.find((s) => s.row === info.row);

  const updateBinding = (patch: Partial<CellBinding>) => {
    if (!binding) return;
    onBindingChange(cellKey, { ...binding, ...patch });
  };

  // 顶部统一预览：当前格配置编译成的表达式（与单元格呈现一致）
  const previewText = summaryRow
    ? (() => {
        const c = summaryRow.cells.find((sc) => sc.column === info.column);
        return c ? summaryCellText(c, datasets) : '';
      })()
    : binding
      ? valueDisplayText(binding.value, datasets, loopBlocks)
      : '';

  return (
    <div className="re-panel">
      <div className="re-panel__title" style={{ paddingLeft: 32 }}>属性面板</div>
      <div className="re-panel__content">
        {/* 单元格信息：位置 + 预览（统一格式） */}
        <div className="re-prop-meta">
          <div className="re-prop-meta__item">
            <div className="re-prop-meta__label">位置</div>
            <div className="re-prop-meta__value">
              {info.a1Notation || `${info.row},${info.column}`}
              {summaryRow && (
                <Badge color="gold" text="汇总行" style={{ marginLeft: 8 }} />
              )}
            </div>
          </div>
          <div className="re-prop-meta__item">
            <div className="re-prop-meta__label">预览</div>
            <div className="re-prop-meta__value">
              <code>{previewText || '（未配置）'}</code>
            </div>
          </div>
        </div>

        {summaryRow ? (
          /* ── 汇总行单元格 ── */
          <SummaryRowEditor
            summaryRow={summaryRow}
            column={info.column}
            datasets={datasets}
            onChange={(row) => onSummaryRowChange(summaryRow.id, row)}
            onDelete={() => onSummaryRowDelete(summaryRow.id)}
          />
        ) : binding ? (
          /* ── 数据绑定单元格 ── */
          <>
            <Tabs
              className="re-prop-tabs"
              size="small"
              items={[
                {
                  key: 'value',
                  label: '值表达式',
                  children: (
                    <ValueEditor
                      value={binding.value}
                      datasets={datasets}
                      loopBlocks={loopBlocks}
                      onChange={(value) => updateBinding({ value })}
                    />
                  ),
                },
                {
                  key: 'expansion',
                  label: '扩展设置',
                  children: (
                    <ExpansionEditor
                      expansion={binding.expansion}
                      expandMode={binding.expandMode}
                      mergeRepeated={binding.mergeRepeated}
                      parentCell={binding.parentCell}
                      cellBindings={cellBindings}
                      currentCellKey={cellKey}
                      onChange={(patch) => updateBinding(patch)}
                    />
                  ),
                },
                {
                  key: 'conditions',
                  label: (
                    <span>
                      过滤条件
                      {binding.conditions.length > 0 && (
                        <Badge
                          count={binding.conditions.length}
                          size="small"
                          style={{ marginLeft: 4 }}
                        />
                      )}
                    </span>
                  ),
                  children: (
                    <ConditionEditor
                      conditions={binding.conditions}
                      datasets={datasets}
                      loopBlocks={loopBlocks}
                      onChange={(conditions) => updateBinding({ conditions })}
                    />
                  ),
                },
              ]}
            />

            <div className="re-prop-actions">
              <Popconfirm
                title="确定删除此绑定？"
                onConfirm={() => onBindingDelete(cellKey)}
                okText="删除"
                cancelText="取消"
              >
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                >
                  删除绑定
                </Button>
              </Popconfirm>
            </div>
          </>
        ) : (
          /* ── 空白单元格 ── */
          <div className="re-prop-unbound">
            <div className="re-prop-unbound__hint">
              此单元格未配置。可绑定数据字段，或将本行设为汇总行（小计/总计）。
            </div>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button
                type="dashed"
                icon={<PlusOutlined />}
                block
                onClick={() => onBindingCreate(cellKey)}
              >
                创建绑定
              </Button>
              <Button
                type="dashed"
                icon={<TableOutlined />}
                block
                onClick={() => onSummaryRowCreate(info.row)}
              >
                将第 {info.row + 1} 行设为汇总行
              </Button>
            </Space>
          </div>
        )}
      </div>
    </div>
  );
};

export default PropertyPanel;
