import React, { useState, useEffect, useRef } from 'react';
import { Button, Popconfirm, Badge, Tabs, Empty, Form } from 'antd';
import { PlusOutlined, DeleteOutlined, MenuFoldOutlined } from '@ant-design/icons';
import type { CellBinding, SummaryRow, LoopBlock, Dataset, ReportParam, ExpressionCatalog } from '../../types';
import type { SheetCellSelectInfo } from '../sheet-panel';
import ExpressionBuilder from './expression-builder';
import ExpansionEditor from './expansion-editor';
import ConditionEditor from './condition-editor';
import SummaryRowEditor from './summary-row-editor';
import DrillEditor from './drill-editor';
import { valueDisplayText } from '../../value-text';
import { cellA1 } from '../../utils/excel-cell';

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
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择一个单元格查看属性" style={{ margin: '32px 0' }} />
        </div>
      </div>
    );
  }

  const { info } = selectedCell;
  const cellKey = `${info.sheetId}:${info.row}:${info.column}`;
  const binding = cellBindings.find((b) => b.cellKey === cellKey);
  // 汇总按列区间归属：点击的列落在某汇总的 [fromColumn, toColumn] 内即命中
  // （同一设计行可并列多个汇总，各占不同列段）
  const summaryRow = summaries.find(
    (s) => s.row === info.row && info.column >= s.fromColumn && info.column <= s.toColumn,
  );

  const updateBinding = (patch: Partial<CellBinding>) => {
    if (!binding) return;
    onBindingChange(cellKey, { ...binding, ...patch });
  };

  // 预览文本
  const previewText = summaryRow
    ? (() => {
        const c = summaryRow.cells.find((sc) => sc.column === info.column);
        return c ? valueDisplayText(c.value, datasets, loopBlocks, params) : '';
      })()
    : binding
      ? valueDisplayText(binding.value, datasets, loopBlocks, params)
      : '';

  // 位置显示（A1 记法）
  const positionText = info.a1Notation || `${info.row + 1},${info.column + 1}`;
  // 汇总行：显示其作用列区间（A1 风格），如 A3:B3；单列时只显示一格
  const summaryRangeText = summaryRow
    ? summaryRow.fromColumn === summaryRow.toColumn
      ? cellA1(summaryRow.row, summaryRow.fromColumn)
      : `${cellA1(summaryRow.row, summaryRow.fromColumn)}:${cellA1(summaryRow.row, summaryRow.toColumn)}`
    : '';

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
              <Form layout="vertical" size="small">
                <Form.Item label="本格内容" tooltip="当前单元格显示什么：文本、字段、聚合、或混合表达式。支持 ${...} 模板语法。">
                  <ExpressionBuilder
                    key={cellKey}
                    value={binding.value}
                    datasets={datasets}
                    loopBlocks={loopBlocks}
                    params={params}
                    functions={functions}
                    onChange={(value) => updateBinding({ value })}
                  />
                </Form.Item>
              </Form>
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
                independent={binding.independent ?? false}
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
              条件{binding.conditions.length > 0 && `(${binding.conditions.length})`}
            </span>
          ),
          children: (
            <div className="re-prop-tab-content">
              <ConditionEditor
                conditions={binding.conditions}
                datasets={datasets}
                loopBlocks={loopBlocks}
                params={params}
                onChange={(conditions) => updateBinding({ conditions })}
              />
            </div>
          ),
        },
        {
          key: 'drill',
          label: '反查',
          children: (
            <div className="re-prop-tab-content">
              <DrillEditor
                drillEnabled={binding.drillEnabled}
                drillView={binding.drillView}
                datasets={datasets}
                defaultView={(() => {
                  // 推断该格字段所属数据集：从 value 中提取 FieldValue 的 datasetId
                  const v = binding.value;
                  if (v.type === 'FieldValue' && v.payload) {
                    const parts = v.payload.split('.');
                    return parts.length >= 2 ? parts[0] : null;
                  }
                  if (v.type === 'Aggregate' && v.operand?.type === 'FieldValue' && v.operand.payload) {
                    const parts = v.operand.payload.split('.');
                    return parts.length >= 2 ? parts[0] : null;
                  }
                  return null;
                })()}
                onChange={(patch) => updateBinding(patch)}
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
          {summaryRow && (
            <span style={{ marginLeft: 8, opacity: 0.65, fontWeight: 400 }}>
              范围:{summaryRangeText}
            </span>
          )}
          {summaryRow && <Badge color="gold" text="汇总行" style={{ marginLeft: 8 }} />}
        </span>
        {(summaryRow || binding) && (
          <Popconfirm
            title={summaryRow ? '取消该汇总配置？' : '确定清除此绑定？'}
            description={summaryRow ? '将移除本汇总（列区间）的所有单元格' : undefined}
            onConfirm={() => {
              if (summaryRow) onSummaryRowDelete(summaryRow.id);
              else if (binding) onBindingDelete(cellKey);
            }}
            okText={summaryRow ? '取消汇总' : '清除'}
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
            loopBlocks={loopBlocks}
            params={params}
            functions={functions}
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
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="未配置绑定。点击下方按钮绑定数据，或框选单元格后右键「设为汇总行」。"
            style={{ margin: '24px 0' }}
          >
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              block
              onClick={() => onBindingCreate(cellKey)}
            >
              创建绑定
            </Button>
          </Empty>
        )}
      </div>
    </div>
  );
};

export default PropertyPanel;
