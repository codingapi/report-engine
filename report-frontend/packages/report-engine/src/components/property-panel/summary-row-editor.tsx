import React from 'react';
import { Button, Input, Select, Radio, Popconfirm, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { SummaryRow, SummaryCell, Dataset, Aggregation } from '../../types';
import { findDataset, AGG_LABELS } from '../../types';
import SectionLabel from './section-label';

interface SummaryRowEditorProps {
  /** 当前选中单元格所在的汇总行 */
  summaryRow: SummaryRow;
  /** 当前选中列 */
  column: number;
  datasets: Dataset[];
  onChange: (row: SummaryRow) => void;
  onDelete: () => void;
}

/** 解析 "datasetId.field" */
function parseRef(ref: string): { datasetId: string; field: string } {
  const dot = ref.indexOf('.');
  if (dot === -1) return { datasetId: '', field: '' };
  return { datasetId: ref.slice(0, dot), field: ref.slice(dot + 1) };
}

const SummaryRowEditor: React.FC<SummaryRowEditorProps> = ({
  summaryRow,
  column,
  datasets,
  onChange,
  onDelete,
}) => {
  const isGroup = summaryRow.groupBy != null;
  const cell = summaryRow.cells.find((c) => c.column === column);

  /** 写入/更新本列单元格 */
  const setCell = (patch: Partial<SummaryCell>) => {
    let cells: SummaryCell[];
    if (cell) {
      cells = summaryRow.cells.map((c) => (c.column === column ? { ...c, ...patch } : c));
    } else {
      cells = [...summaryRow.cells, { column, kind: 'label', payload: '', ...patch }];
    }
    onChange({ ...summaryRow, cells });
  };

  /** 移除本列单元格 */
  const clearCell = () => {
    onChange({ ...summaryRow, cells: summaryRow.cells.filter((c) => c.column !== column) });
  };

  const { datasetId } = parseRef(cell?.payload || '');

  return (
    <div>
      {/* 汇总范围（整行） */}
      <div className="re-prop-exp-section">
        <SectionLabel
          text="汇总范围"
          hint="总计：在数据末尾追加一行；分组小计：按指定字段每组追加一行小计。作用于整行。"
        />
        <Radio.Group
          size="small"
          value={isGroup ? 'group' : 'total'}
          onChange={(e) =>
            onChange({
              ...summaryRow,
              groupBy:
                e.target.value === 'group'
                  ? { datasetId: datasets[0]?.id || '', field: '' }
                  : null,
            })
          }
          optionType="button"
          buttonStyle="solid"
        >
          <Radio.Button value="total">总计</Radio.Button>
          <Radio.Button value="group">分组小计</Radio.Button>
        </Radio.Group>

        {isGroup && (
          <div className="re-prop-field-cascade" style={{ marginTop: 8 }}>
            <Select
              size="small"
              value={summaryRow.groupBy!.datasetId || undefined}
              onChange={(dsId) => onChange({ ...summaryRow, groupBy: { datasetId: dsId, field: '' } })}
              placeholder="数据集"
              options={datasets.map((d) => ({ value: d.id, label: d.alias || d.id }))}
              showSearch
            />
            <Select
              size="small"
              value={summaryRow.groupBy!.field || undefined}
              onChange={(field) => onChange({ ...summaryRow, groupBy: { ...summaryRow.groupBy!, field } })}
              placeholder="分组字段"
              disabled={!summaryRow.groupBy!.datasetId}
              options={
                findDataset(datasets, summaryRow.groupBy!.datasetId)?.fields.map((f) => ({
                  value: f.name,
                  label: f.alias || f.name,
                })) || []
              }
              showSearch
            />
          </div>
        )}
      </div>

      {/* 本格内容 */}
      <div className="re-prop-exp-section">
        <SectionLabel
          text="本格内容"
          hint="当前选中列在汇总行显示什么：固定文本（如 合计，分组小计支持 ${group} 占位当前分组值）或对某字段的聚合值。"
        />

        {!cell ? (
          <Space>
            <Button size="small" onClick={() => setCell({ kind: 'label', payload: isGroup ? '${group}小计' : '合计' })}>
              设为文本
            </Button>
            <Button size="small" onClick={() => setCell({ kind: 'agg', payload: '', aggregation: 'SUM' })}>
              设为聚合
            </Button>
          </Space>
        ) : (
          <>
            <Radio.Group
              size="small"
              value={cell.kind}
              onChange={(e) => {
                const kind = e.target.value as 'label' | 'agg';
                setCell({ kind, payload: '', aggregation: kind === 'agg' ? 'SUM' : undefined });
              }}
              optionType="button"
              buttonStyle="solid"
              style={{ marginBottom: 8 }}
            >
              <Radio.Button value="label">文本</Radio.Button>
              <Radio.Button value="agg">聚合</Radio.Button>
            </Radio.Group>

            {cell.kind === 'label' ? (
              <Input
                size="small"
                value={cell.payload}
                onChange={(e) => setCell({ payload: e.target.value })}
                placeholder={isGroup ? '${group}小计' : '合计'}
              />
            ) : (
              <div className="re-prop-field-cascade">
                <Select
                  size="small"
                  value={datasetId || undefined}
                  onChange={(dsId) => setCell({ payload: `${dsId}.` })}
                  placeholder="数据集"
                  options={datasets.map((d) => ({ value: d.id, label: d.alias || d.id }))}
                  showSearch
                />
                <Select
                  size="small"
                  value={cell.payload || undefined}
                  onChange={(ref) => setCell({ payload: ref })}
                  placeholder="字段"
                  disabled={!datasetId}
                  options={
                    findDataset(datasets, datasetId)?.fields.map((f) => ({
                      value: `${datasetId}.${f.name}`,
                      label: f.alias || f.name,
                    })) || []
                  }
                  showSearch
                />
                <Select
                  size="small"
                  value={cell.aggregation || 'SUM'}
                  onChange={(agg: Aggregation) => setCell({ aggregation: agg })}
                  style={{ flex: '0 0 76px' }}
                  options={Object.entries(AGG_LABELS)
                    .filter(([k]) => k !== 'NONE')
                    .map(([v, l]) => ({ value: v, label: l }))}
                />
              </div>
            )}

            <Button
              type="link"
              size="small"
              danger
              onClick={clearCell}
              style={{ padding: 0, marginTop: 4 }}
            >
              清空本格
            </Button>
          </>
        )}
      </div>

      {/* 删除整行 */}
      <div className="re-prop-actions">
        <Popconfirm
          title="取消本行的汇总配置？"
          description="将移除整行所有汇总单元格"
          onConfirm={onDelete}
          okText="取消汇总"
          cancelText="返回"
        >
          <Button type="text" danger size="small" icon={<DeleteOutlined />}>
            取消汇总行
          </Button>
        </Popconfirm>
      </div>
    </div>
  );
};

export default SummaryRowEditor;
