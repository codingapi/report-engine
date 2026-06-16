import React from 'react';
import { Empty, Tag } from 'antd';
import { SwapOutlined } from '@ant-design/icons';
import type { Relationship, Dataset } from '../../types';
import { findDataset } from '../../types';

interface RelationshipListProps {
  relationships: Relationship[];
  datasets: Dataset[];
}

/** 只读展示数据关系：数据集A.字段 ↔ 数据集B.字段 (JOIN) */
const RelationshipList: React.FC<RelationshipListProps> = ({ relationships, datasets }) => {
  const endpoint = (ref: { datasetId: string; field: string }) => {
    const ds = findDataset(datasets, ref.datasetId);
    const dsName = ds?.alias || ref.datasetId;
    const fieldName = ds?.fields.find((f) => f.name === ref.field)?.alias || ref.field;
    return `${dsName}.${fieldName}`;
  };

  if (relationships.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无数据关系"
        style={{ margin: '24px 0' }}
      />
    );
  }

  return (
    <div className="re-rel-list">
      {relationships.map((r, i) => (
        <div key={i} className="re-rel-item">
          <span className="re-rel-endpoint">{endpoint(r.left)}</span>
          <SwapOutlined className="re-rel-icon" />
          <span className="re-rel-endpoint">{endpoint(r.right)}</span>
          <Tag color="blue" style={{ marginLeft: 'auto' }}>{r.joinType}</Tag>
        </div>
      ))}
    </div>
  );
};

export default RelationshipList;
