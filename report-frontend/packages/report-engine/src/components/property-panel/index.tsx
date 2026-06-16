import React, { useState, useEffect, useRef } from 'react';
import { Button, Popconfirm, Badge, Space, Tabs } from 'antd';
import { PlusOutlined, DeleteOutlined, TableOutlined, MenuFoldOutlined } from '@ant-design/icons';
import type { CellBinding, SummaryRow, LoopBlock, Dataset, ReportParam, ExpressionCatalog } from '../../types';
import type { SheetCellSelectInfo } from '../sheet-panel';
import ExpressionBuilder from './expression-builder';
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
  params: ReportParam[];
  functions?: ExpressionCatalog;
  onBindingChange: (cellKey: string, binding: CellBinding) => void;
  onBindingCreate: (cellKey: string) => void;
  onBindingDelete: (cellKey: string) => void;
  onSummaryRowChange: (id: string, row: SummaryRow) => void;
  onSummaryRowCreate: (row: number) => void;
  onSummaryRowDelete: (id: string) => void;
  onCollapse?: () => void;
}

const PropertyPanel: React.FC<PropertyPanelProps> = ({
  selectedCell,
  cellBindings,
  summaries,
  loopBlocks,
  datasets,
  params,
  functions,
  onBindingChange,
  onBindingCreate,
  onBindingDelete,
  onSummaryRowChange,
  onSummaryRowCreate,
  onSummaryRowDelete,
  onCollapse,
}) => {
  const [activeTab, setActiveTab] = useState<string>('content');
  const prevCellKeyRef = useRef<string | null>(null);

  // 切换单元格时重置 Tab 到"内容"
  useEffect(() => {
    if (!selectedCell) return;
    const { info } = selectedCell;
    const cellKey = `${info.sheetId}:${info.row}:${info.column}`;
    if (cellKey !== prevCellKeyRef.current) {
      prevCellKeyRef.current = cellKey;
      setActiveTab('content');
    }
  }, [selectedCell]);

  // ─── 空态 ───
  if (!selectedCell) {
    return (
      <div className="re-panel">
        <div className="re-panel__title">
          {onCollapse && (
            <Button
              type="text"
              size="small"
              icon={<MenuFoldOutlined />}
              onClick={onCollapse}
            />
          )}
          <span>属性面板</span>
        </div>
        <div className="re-panel__content">
          <div className="re-prop-empty">选择一个单元格查看属性</div>
        </div>
      </div>
    );
  }

  const { info } = selectedCell;
  const cellKey = `${info.sheetId}:${info.row}:${info.column}`;
  const binding = cellBindings.find((b) => b.cellKey === cellKey);
  const summaryRow = summaries.find((s) => s.row === info.row);

  const updateBinding = (patch: Partial<CellBinding>) => {
    if (!binding) return;
    onBindingChange(cellKey, { ...binding, ...patch });
  };

  // 预览文本
  const previewText = summaryRow
    ? (() => {
        const c = summaryRow.cells.find((sc) => sc.column === info.column);
        return c ? summaryCellText(c, datasets) : '';
      })()
    : binding
      ? valueDisplayText(binding.value, datasets, loopBlocks)
      : '';

  // 位置显示（A1 记法）
  const positionText = info.a1Notation || `${info.row + 1},${info.column + 1}`;

  // ─── 已绑定：Tab 内容 ───
  const bindingTabItems = binding
    ? [
        {
          key: 'content',
          label: '内容',
          children: (
            <div className="re-prop-tab-content">
              <div className="re-prop-preview">
                <div className="re-prop-preview__label">预览</div>
                <code>{previewText || '（未配置）'}</code>
              </div>
              <ExpressionBuilder
                key={cellKey}
                value={binding.value}
                datasets={datasets}
                loopBlocks={loopBlocks}
                params={params}
                functions={functions}
                onChange={(value) => updateBinding({ value })}
              />
            </div>
          ),
        },
        {
          key: 'expansion',
          label: '扩展',
          children: (
            <div className="re-prop-tab-content">
              <ExpansionEditor
                expansion={binding.expansion}
                expandMode={binding.expandMode}
                mergeRepeated={binding.mergeRepeated}
                parentCell={binding.parentCell}
                cellBindings={cellBindings}
                currentCellKey={cellKey}
                onChange={(patch) => updateBinding(patch)}
              />
            </div>
          ),
        },
        {
          key: 'condition',
          label: (
            <span>
              条件
              {binding.conditions.length > 0 && (
                <Badge count={binding.conditions.length} size="small" style={{ marginLeft: 4 }} />
              )}
            </span>
          ),
          children: (
            <div className="re-prop-tab-content">
              <ConditionEditor
                conditions={binding.conditions}
                datasets={datasets}
                loopBlocks={loopBlocks}
                onChange={(conditions) => updateBinding({ conditions })}
              />
            </div>
          ),
        },
      ]
    : [];

  return (
    <div className="re-panel">
      {/* ─── Title 栏：收缩 + 位置/标题 + 清空操作 ─── */}
      <div className="re-panel__title">
        {onCollapse && (
          <Button
            type="text"
            size="small"
            icon={<MenuFoldOutlined />}
            onClick={onCollapse}
          />
        )}
        <span className="re-panel__title-text">
          {positionText}
          {summaryRow && <Badge color="gold" text="汇总行" style={{ marginLeft: 8 }} />}
        </span>
        {(summaryRow || binding) && (
          <Popconfirm
            title={summaryRow ? '取消本行的汇总配置？' : '确定清除此绑定？'}
            description={summaryRow ? '将移除整行所有汇总单元格' : undefined}
            onConfirm={() => {
              if (summaryRow) onSummaryRowDelete(summaryRow.id);
              else if (binding) onBindingDelete(cellKey);
            }}
            okText={summaryRow ? '取消汇总' : '清除'}
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />}>
              清空
            </Button>
          </Popconfirm>
        )}
      </div>

      <div className="re-panel__content">
        {summaryRow ? (
          /* ── 汇总行：单页展示 ── */
          <SummaryRowEditor
            summaryRow={summaryRow}
            column={info.column}
            datasets={datasets}
            onChange={(row) => onSummaryRowChange(summaryRow.id, row)}
          />
        ) : binding ? (
          /* ── 数据绑定：Tab 切换 ── */
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            size="small"
            className="re-prop-tabs"
            items={bindingTabItems}
          />
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
