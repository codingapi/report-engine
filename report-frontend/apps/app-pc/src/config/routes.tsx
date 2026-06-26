import { CloudServerOutlined, DatabaseOutlined, HomeOutlined, TableOutlined, ApiOutlined } from '@ant-design/icons';
import HomePage from '@/pages/home';
import ReportsPage from '@/pages/reports';
import AppReport from '@/pages/engine';
import AppPreview from '@/pages/preview';
import DataModelsPage from '@/pages/datamodels';
import DatamodelDesignPage from '@/pages/datamodel-design';
import DataSourceTypesPage from '@/pages/datasource-types';
import DataSourcesPage from '@/pages/datasources';
import React from 'react';

export interface RouteConfig {
  path: string;
  name: string;
  icon: React.ReactNode;
  element: React.ReactNode;
}

const routes: RouteConfig[] = [
  {
    path: '/',
    name: '首页',
    icon: <HomeOutlined />,
    element: <HomePage />,
  },
  {
    path: '/reports',
    name: '报表管理',
    icon: <TableOutlined />,
    element: <ReportsPage />,
  },
  {
    path: '/datamodels',
    name: '数据模型',
    icon: <DatabaseOutlined />,
    element: <DataModelsPage />,
  },
  {
    path: '/datamodels/:id',
    name: '数据模型设计',
    icon: <DatabaseOutlined />,
    element: <DatamodelDesignPage />,
  },
  {
    path: '/datasource-types',
    name: '数据源类型',
    icon: <ApiOutlined />,
    element: <DataSourceTypesPage />,
  },
  {
    path: '/datasources',
    name: '数据源',
    icon: <CloudServerOutlined />,
    element: <DataSourcesPage />,
  },
  {
    path: '/engine',
    name: '报表设计器',
    icon: <TableOutlined />,
    element: <AppReport />,
  },
  {
    path: '/preview',
    name: '报表预览',
    icon: <TableOutlined />,
    element: <AppPreview />,
  },
];

export default routes;
