import React, { useEffect, useRef, useState } from 'react';
import { Form, Select, Radio, Alert, Space, Tabs } from 'antd';
import type { SummaryRow, SummaryCell, Dataset, LoopBlock, ReportParam, ExpressionCatalog, ReportValue } from '../../types';
import { findDataset } from '../../types';
import { datasetOptions, fieldOptions } from '../../utils/dataset-options';
import { valueDisplayText } from '../../value-text';
import ExpressionBuilder from './expression-builder';
import DrillEditor from './drill-editor';

interface SummaryRowEditorProps {
  summaryRow: SummaryRow;
  /** 当前选中格在交叉轴上的坐标（纵向=列号、横向=行号） */
  crossPos: number;
  datasets: Dataset[];
  loopBlocks: LoopBlock[];
  params: ReportParam[];
  functions?: ExpressionCatalog;
  onChange: (row: SummaryRow) => void;
}

const SummaryRowEditor: React.FC<SummaryRowEditorProps> = ({
  summaryRow,
  crossPos,
  datasets,
  loopBlocks,
  params,
  functions,
  onChange,
}) => {
  const isGroup = summaryRow.groupBy != null;
  const isHorizontal = (summaryRow.axis ?? 'VERTICAL') === 'HORIZONTAL';
  const cell = summaryRow.cells.find((c) => c.crossPos === crossPos);
  const initializedRef = useRef<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('content');

  // 切换选中格或汇总时重置 Tab 到"内容"
  const prevKeyRef = useRef<string | null>(null);
  useEffect(() => {
    const key = `${summaryRow.id}-${crossPos}`;
    if (key !== prevKeyRef.current) {
      prevKeyRef.current = key;
      setActiveTab('content');
    }
  }, [summaryRow.id, crossPos]);

  /** 创建空值（由用户自行填写） */
  const createEmptyValue = (): ReportValue => {
    return { type: 'Literal', payload: '' };
  };

  /** 写入/更新本格单元格 */
  const setCellValue = (value: ReportValue) => {
    let cells: SummaryCell[];
    if (cell) {
      cells = summaryRow.cells.map((c) => (c.crossPos === crossPos ? { ...c, value } : c));
    } else {
      cells = [...summaryRow.cells, { crossPos, value }];
    }
    onChange({ ...summaryRow, cells });
  };

  /** 更新反查配置 */
  const setDrillConfig = (patch: { drillEnabled?: boolean; drillView?: string | null }) => {
    if (!cell) return;
    const cells = summaryRow.cells.map((c) =>
      c.crossPos === crossPos ? { ...c, ...patch } : c
    );
    onChange({ ...summaryRow, cells });
  };

  // 自动初始化：当切换到未配置的格时，自动创建空值
  useEffect(() => {
    const cellKey = `${summaryRow.id}-${crossPos}`;
    if (!cell && initializedRef.current !== cellKey) {
      initializedRef.current = cellKey;
      setCellValue(createEmptyValue());
    }
  }, [summaryRow.id, crossPos, cell]);

  // 当前分组字段别名（用于 ${group} 说明）
  const groupFieldLabel = isGroup
    ? findDataset(datasets, summaryRow.groupBy!.datasetId)?.fields.find(
        (f) => f.name === summaryRow.groupBy!.field,
      )?.alias || summaryRow.groupBy!.field || '分组'
    : '';

  // 推断该格字段所属数据集：从 value 中提取 FieldValue 的 datasetId
  const defaultDrillView = (() => {
    if (!cell) return null;
    const v = cell.value;
    if (v.type === 'FieldValue' && v.payload) {
      const parts = v.payload.split('.');
      return parts.length >= 2 ? parts[0] : null;
    }
    if (v.type === 'Aggregate' && v.operand?.type === 'FieldValue' && v.operand.payload) {
      const parts = v.operand.payload.split('.');
      return parts.length >= 2 ? parts[0] : null;
    }
    return null;
  })();

  const tabItems = [
    {
      key: 'content',
      label: '内容',
      children: (
        <div className="re-prop-tab-content">
          {cell && (
            <div className="re-prop-preview">
              <div className="re-prop-preview__label">预览</div>
              <code>{valueDisplayText(cell.value, datasets, loopBlocks, params) || '（空）'}</code>
            </div>
          )}

          <Form layout="vertical" size="small">
            <Form.Item label="汇总范围" tooltip={isHorizontal
              ? '总计：在数据带右侧追加一列；分组小计：按指定字段每组追加一列小计。作用范围为右键框选的行区间。'
              : '总计：在数据带末尾追加一行；分组小计：按指定字段每组追加一行小计。作用范围为右键框选的列区间。'}>
              <Radio.Group
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
                <Space.Compact style={{ width: '100%', marginTop: 8 }}>
                  <Select
                    value={summaryRow.groupBy!.datasetId || undefined}
                    onChange={(dsId) => onChange({ ...summaryRow, groupBy: { datasetId: dsId, field: '' } })}
                    placeholder="数据集"
                    options={datasetOptions(datasets)}
                    showSearch
                    style={{ flex: 1, minWidth: 0 }}
                  />
                  <Select
                    value={summaryRow.groupBy!.field || undefined}
                    onChange={(field) => onChange({ ...summaryRow, groupBy: { ...summaryRow.groupBy!, field } })}
                    placeholder="分组字段"
                    disabled={!summaryRow.groupBy!.datasetId}
                    options={fieldOptions(datasets, summaryRow.groupBy!.datasetId)}
                    showSearch
                    style={{ flex: 1, minWidth: 0 }}
                  />
                </Space.Compact>
              )}
            </Form.Item>

            <Form.Item label="本格内容" tooltip="当前选中格在汇总显示什么：文本、聚合、或混合表达式。支持 ${...} 模板语法。">
              {cell && (
                <>
                  <ExpressionBuilder
                    key={`${summaryRow.id}-${crossPos}`}
                    value={cell.value}
                    datasets={datasets}
                    loopBlocks={loopBlocks}
                    params={params}
                    functions={functions}
                    onChange={setCellValue}
                  />

                  {isGroup && (
                    <Alert
                      type="info"
                      showIcon={false}
                      message={<>可用 <code>{'${group}'}</code> 代表当前分组值（即「{groupFieldLabel}」的每个取值，渲染时注入）</>}
                      style={{ marginTop: 8, fontSize: 12 }}
                    />
                  )}
                </>
              )}
            </Form.Item>
          </Form>
        </div>
      ),
    },
    {
      key: 'drill',
      label: '反查',
      children: (
        <div className="re-prop-tab-content">
          {cell ? (
            <DrillEditor
              drillEnabled={cell.drillEnabled}
              drillView={cell.drillView}
              datasets={datasets}
              defaultView={defaultDrillView}
              onChange={setDrillConfig}
            />
          ) : (
            <div style={{ padding: '24px 0', textAlign: 'center', color: '#999', fontSize: 12 }}>
              请先配置本格内容
            </div>
          )}
        </div>
      ),
    },
  ];

  return (
    <Tabs
      activeKey={activeTab}
      onChange={setActiveTab}
      size="small"
      className="re-prop-tabs"
      items={tabItems}
    />
  );
};

export default SummaryRowEditor;
