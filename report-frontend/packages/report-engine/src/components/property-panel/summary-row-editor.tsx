import React, { useEffect, useRef } from 'react';
import { Select, Radio } from 'antd';
import type { SummaryRow, SummaryCell, Dataset, LoopBlock, ReportParam, ExpressionCatalog, ReportValue } from '../../types';
import { findDataset } from '../../types';
import { valueDisplayText } from '../../value-text';
import SectionLabel from './section-label';
import ExpressionBuilder from './expression-builder';

interface SummaryRowEditorProps {
  summaryRow: SummaryRow;
  column: number;
  datasets: Dataset[];
  loopBlocks: LoopBlock[];
  params: ReportParam[];
  functions?: ExpressionCatalog;
  onChange: (row: SummaryRow) => void;
}

const SummaryRowEditor: React.FC<SummaryRowEditorProps> = ({
  summaryRow,
  column,
  datasets,
  loopBlocks,
  params,
  functions,
  onChange,
}) => {
  const isGroup = summaryRow.groupBy != null;
  const cell = summaryRow.cells.find((c) => c.column === column);
  const initializedRef = useRef<string | null>(null);

  /** 创建空值（由用户自行填写） */
  const createEmptyValue = (): ReportValue => {
    return { type: 'Literal', payload: '' };
  };

  /** 写入/更新本列单元格 */
  const setCellValue = (value: ReportValue) => {
    let cells: SummaryCell[];
    if (cell) {
      cells = summaryRow.cells.map((c) => (c.column === column ? { ...c, value } : c));
    } else {
      cells = [...summaryRow.cells, { column, value }];
    }
    onChange({ ...summaryRow, cells });
  };

  // 自动初始化：当切换到未配置的列时，自动创建空值
  useEffect(() => {
    const cellKey = `${summaryRow.id}-${column}`;
    if (!cell && initializedRef.current !== cellKey) {
      initializedRef.current = cellKey;
      setCellValue(createEmptyValue());
    }
  }, [summaryRow.id, column, cell]);

  // 当前分组字段别名（用于 ${group} 说明）
  const groupFieldLabel = isGroup
    ? findDataset(datasets, summaryRow.groupBy!.datasetId)?.fields.find(
        (f) => f.name === summaryRow.groupBy!.field,
      )?.alias || summaryRow.groupBy!.field || '分组'
    : '';

  return (
    <div>
      {/* 预览 */}
      {cell && (
        <div className="re-prop-preview">
          <div className="re-prop-preview__label">预览</div>
          <code>{valueDisplayText(cell.value, datasets, loopBlocks) || '（空）'}</code>
        </div>
      )}

      {/* 汇总范围（列区间） */}
      <div className="re-prop-exp-section">
        <SectionLabel
          text="汇总范围"
          hint="总计：在数据带末尾追加一行；分组小计：按指定字段每组追加一行小计。作用范围为右键框选的列区间。"
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
          hint="当前选中列在汇总行显示什么：文本、聚合、或混合表达式。支持 ${...} 模板语法。"
        />

        {cell && (
          <>
            {/* 表达式编辑器 */}
            <ExpressionBuilder
              key={`${summaryRow.id}-${column}`}
              value={cell.value}
              datasets={datasets}
              loopBlocks={loopBlocks}
              params={params}
              functions={functions}
              onChange={setCellValue}
            />

            {/* 分组小计提示 */}
            {isGroup && (
              <div className="re-prop-group-hint">
                可用 <code>{'${group}'}</code> 代表当前分组值（即「{groupFieldLabel}」的每个取值，渲染时注入）
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default SummaryRowEditor;
