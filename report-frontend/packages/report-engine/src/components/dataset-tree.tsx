import React from 'react';
import { Tree } from 'antd';
import { DatabaseOutlined, TableOutlined, KeyOutlined } from '@ant-design/icons';
import type { Dataset } from '../types';

interface DatasetTreeProps {
  datasets: Dataset[];
}

interface FieldDragData {
  datasetId: string;
  field: string;
  alias: string;
}

/**
 * 数据集树：展示数据集和字段的层级结构，字段节点支持拖拽。
 */
const DatasetTree: React.FC<DatasetTreeProps> = ({ datasets }) => {
  const treeData = datasets.map((ds) => ({
    key: ds.id,
    title: (
      <span>
        <TableOutlined style={{ marginRight: 4 }} />
        {ds.alias || ds.id}
      </span>
    ),
    selectable: false,
    children: ds.fields.map((f) => ({
      key: `${ds.id}.${f.name}`,
      title: (
        <span
          className="re-field-drag"
          draggable
          onDragStart={(e) => {
            const data: FieldDragData = {
              datasetId: ds.id,
              field: f.name,
              alias: f.alias || f.name,
            };
            e.dataTransfer.setData('text/plain', JSON.stringify(data));
            e.dataTransfer.effectAllowed = 'copy';
          }}
        >
          <span style={{ marginRight: 4 }}>{f.alias || f.name}</span>
          <span className="re-field-type">{f.dataType}</span>
          {f.primaryKey && <KeyOutlined className="re-field-pk" style={{ marginLeft: 4 }} />}
        </span>
      ),
      isLeaf: true,
      selectable: false,
    })),
  }));

  return (
    <div className="re-dataset-tree">
      <Tree
        treeData={treeData}
        defaultExpandAll
        blockNode
        showLine={{ showLeafIcon: false }}
      />
    </div>
  );
};

export default DatasetTree;
