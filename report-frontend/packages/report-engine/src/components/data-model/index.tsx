import React from 'react';
import { Tabs, Badge } from 'antd';
import type { Dataset, Relationship, ReportParam } from '../../types';
import DatasetTree from '../dataset-tree';
import RelationshipList from './relationship-list';
import ParamManager from './param-manager';

interface DataModelPanelProps {
  datasets: Dataset[];
  relationships: Relationship[];
  params: ReportParam[];
  onParamsChange: (params: ReportParam[]) => void;
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
      <div className="re-panel__title">数据模型</div>
      <div className="re-panel__content">
        <Tabs
          size="small"
          className="re-dm-tabs"
          items={[
            {
              key: 'datasets',
              label: (
                <span>
                  数据集
                  <Badge count={datasets.length} showZero size="small" style={{ marginLeft: 4 }} />
                </span>
              ),
              children: <DatasetTree datasets={datasets} relationships={relationships} />,
            },
            {
              key: 'relations',
              label: (
                <span>
                  数据关系
                  <Badge count={relationships.length} showZero size="small" style={{ marginLeft: 4 }} />
                </span>
              ),
              children: <RelationshipList relationships={relationships} datasets={datasets} />,
            },
            {
              key: 'params',
              label: (
                <span>
                  报表参数
                  <Badge count={params.length} showZero size="small" style={{ marginLeft: 4 }} />
                </span>
              ),
              children: <ParamManager params={params} onChange={onParamsChange} />,
            },
          ]}
        />
      </div>
    </div>
  );
};

export default DataModelPanel;
