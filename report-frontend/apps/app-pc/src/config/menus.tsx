import { ApiOutlined, CloudServerOutlined, DatabaseOutlined, HomeOutlined, TableOutlined } from '@ant-design/icons';

export const menuItems = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/reports', icon: <TableOutlined />, label: '报表管理' },
  { key: '/datamodels', icon: <DatabaseOutlined />, label: '数据模型' },
  { key: '/datasource-types', icon: <ApiOutlined />, label: '数据源类型' },
  { key: '/datasources', icon: <CloudServerOutlined />, label: '数据源' },
];
