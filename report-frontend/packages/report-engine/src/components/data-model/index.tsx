import React from 'react';
import { Tabs } from 'antd';
import type { Dataset, Relationship } from '@/types';
import DatasetTree from '@/components/dataset-tree';
import RelationshipList from './relationship-list';

interface DataModelPanelProps {
  datasets: Dataset[];
  relationships: Relationship[];
}

/**
 * 左侧数据模型面板：数据集 / 数据关系 两 tab。
 * 数据集、关系来自后端 DataModel（只读）。
 */
const DataModelPanel: React.FC<DataModelPanelProps> = ({
  datasets,
  relationships,
}) => {
  return (
    <div className="re-panel">
      <div className="re-panel__title" style={{ paddingLeft: 12 }}>
        数据模型
      </div>
      <div className="re-panel__content">
        <Tabs
          size="small"
          className="re-dm-tabs"
          items={[
            {
              key: 'datasets',
              label: `数据集(${datasets.length})`,
              children: <DatasetTree datasets={datasets} relationships={relationships} />,
            },
            {
              key: 'relations',
              label: `数据关系(${relationships.length})`,
              children: <RelationshipList relationships={relationships} datasets={datasets} />,
            },
          ]}
        />
      </div>
    </div>
  );
};

export default DataModelPanel;
