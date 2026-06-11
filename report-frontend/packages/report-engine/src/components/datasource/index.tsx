import React from 'react';
import { Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  TableOutlined,
  FieldStringOutlined,
  KeyOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { DataConfig, DataType } from './types';

interface DataSourcePanelProps {
  dataConfig: DataConfig;
}

const DataTypeIcon: React.FC<{ dataType: DataType }> = ({ dataType }) => {
  const iconMap: Record<DataType, string> = {
    [DataType.STRING]: 'S',
    [DataType.NUMBER]: 'N',
    [DataType.DATE]: 'D',
    [DataType.DATETIME]: 'T',
    [DataType.BOOLEAN]: 'B',
    [DataType.JSON]: 'J',
  };
  return <span style={{ fontSize: 12, color: '#999', marginRight: 4 }}>{iconMap[dataType]}</span>;
};

const DataSourcePanel: React.FC<DataSourcePanelProps> = ({ dataConfig }) => {
  const treeData: DataNode[] = dataConfig.tables.map((table) => ({
    key: `table-${table.id}`,
    title: (
      <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <TableOutlined />
        <span>{table.alias || table.name}</span>
      </span>
    ),
    selectable: false,
    children: table.fields.map((field) => ({
      key: `field-${table.id}-${field.name}`,
      title: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
          <DataTypeIcon dataType={field.dataType} />
          <span>{field.alias || field.name}</span>
          {field.isPrimary && <KeyOutlined style={{ fontSize: 12, color: '#faad14' }} />}
          {field.foreignKey && <LinkOutlined style={{ fontSize: 12, color: '#1890ff' }} />}
        </span>
      ),
      selectable: false,
    })),
  }));

  return (
    <div style={{ padding: '8px 0' }}>
      <Tree
        treeData={treeData}
        defaultExpandAll
        blockNode
        showLine={{ showLeafIcon: false }}
      />
    </div>
  );
};

export default DataSourcePanel;
