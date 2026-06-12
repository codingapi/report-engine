import React, { useMemo, useCallback } from 'react';
import { Empty, Tabs, Tag } from 'antd';
import type { DataConfig } from '../datasource/types';
import {
  type SelectedCellInfo,
  type CellPropertyMap,
  type CellPropertyConfig,
  type CellKey,
  type LoopBlockConfig,
  EMPTY_CELL_CONFIG,
  makeCellKey,
} from './types';
import ConditionSection from './condition-section';
import CalcMethodSection from './calc-method-section';
import LoopBlockSection from './loop-block-section';

export interface PropertyPanelProps {
  selectedCell: SelectedCellInfo | null;
  dataConfig?: DataConfig;
  cellProperties: CellPropertyMap;
  onCellPropertyChange: (cellKey: CellKey, config: CellPropertyConfig) => void;
  loopBlocks: Record<string, LoopBlockConfig>;
  onLoopBlockChange: (id: string, config: LoopBlockConfig) => void;
  onLoopBlockRemove: (id: string) => void;
}

const PropertyPanel: React.FC<PropertyPanelProps> = ({
  selectedCell,
  dataConfig,
  cellProperties,
  onCellPropertyChange,
  loopBlocks,
  onLoopBlockChange,
  onLoopBlockRemove,
}) => {
  const cellKey = useMemo(() => {
    if (!selectedCell) return null;
    return makeCellKey(selectedCell.sheetId, selectedCell.row, selectedCell.column);
  }, [selectedCell]);

  const config = useMemo(() => {
    if (!cellKey) return EMPTY_CELL_CONFIG;
    return cellProperties[cellKey] ?? EMPTY_CELL_CONFIG;
  }, [cellKey, cellProperties]);

  /** 从条件中提取第一个非空字段，用于过滤计算方式 */
  const firstSelectedField = useMemo(() => {
    const allConditions = [...config.yConditions, ...config.xConditions];
    const first = allConditions.find((c) => c.field);
    return first?.field ?? null;
  }, [config]);

  const handleChange = useCallback(
    (partial: Partial<CellPropertyConfig>) => {
      if (!cellKey) return;
      onCellPropertyChange(cellKey, { ...config, ...partial });
    },
    [cellKey, config, onCellPropertyChange],
  );

  // 空状态（未选中单元格或无数据源）
  if (!selectedCell || !dataConfig) {
    return (
      <div className="report-engine__property-panel">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="请选择一个单元格"
          className="report-engine__property-empty"
        />
      </div>
    );
  }

  /** 单元格属性 tab 内容 */
  const cellTab = (
    <>
      {/* 单元格信息栏 */}
      <div className="report-engine__property-cell-info">
        <div className="report-engine__property-cell-ref">
          <Tag color="blue">{selectedCell.a1Notation}</Tag>
          {selectedCell.value != null && selectedCell.value !== '' && (
            <span className="report-engine__property-cell-value">
              {String(selectedCell.value)}
            </span>
          )}
        </div>
        {selectedCell.mergeRange && (
          <div className="report-engine__property-merge-hint">
            <Tag color="orange" style={{ marginRight: 0 }}>合并单元格</Tag>
            <span>{selectedCell.mergeRange.a1Notation}</span>
          </div>
        )}
      </div>

      {/* 横向条件 (Y轴) */}
      <ConditionSection
        title="横向条件 (Y轴)"
        axis="y"
        conditions={config.yConditions}
        dataConfig={dataConfig}
        onChange={(yConditions) => handleChange({ yConditions })}
      />

      {/* 纵向条件 (X轴) */}
      <ConditionSection
        title="纵向条件 (X轴)"
        axis="x"
        conditions={config.xConditions}
        dataConfig={dataConfig}
        onChange={(xConditions) => handleChange({ xConditions })}
      />

      {/* 计算方式 */}
      <CalcMethodSection
        value={config.calcMethod}
        selectedField={firstSelectedField}
        dataConfig={dataConfig}
        onChange={(calcMethod) => handleChange({ calcMethod })}
      />
    </>
  );

  return (
    <div className="report-engine__property-panel">
      <Tabs
        defaultActiveKey="cell"
        size="small"
        items={[
          { key: 'cell', label: '单元格属性', children: cellTab },
          {
            key: 'loopBlock',
            label: '循环块',
            children: (
              <LoopBlockSection
                selectedCell={selectedCell}
                dataConfig={dataConfig}
                loopBlocks={loopBlocks}
                onLoopBlockChange={onLoopBlockChange}
                onLoopBlockRemove={onLoopBlockRemove}
              />
            ),
          },
        ]}
      />
    </div>
  );
};

export default PropertyPanel;
