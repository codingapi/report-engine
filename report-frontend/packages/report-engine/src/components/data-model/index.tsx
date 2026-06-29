import React from 'react';
import { Tabs } from 'antd';
import type { Dataset, ParamDTO, Relationship } from '@/types';
import DatasetTree from '@/components/dataset-tree';
import RelationshipList from './relationship-list';
import ParamManager from './param-manager';

interface DataModelPanelProps {
  datasets: Dataset[];
  relationships: Relationship[];
  params: ParamDTO[];
  onParamsChange: (params: ParamDTO[]) => void;
}

/**
 * 左侧数据模型面板：数据集 / 数据关系 / 报表参数 三 tab。
 * 数据集、关系来自后端 DataModel（只读）；参数为报表级配置（前端管理）。
 */
const DataModelPanel: React.FC<DataModelPanelProps> = ({
  datasets,
  relationships,
  params,
  onParamsChange,
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
            {
              key: 'params',
              label: `报表参数(${params.length})`,
              children: <ParamManager params={params} onChange={onParamsChange} />,
            },
          ]}
        />
      </div>
    </div>
  );
};

export default DataModelPanel;
