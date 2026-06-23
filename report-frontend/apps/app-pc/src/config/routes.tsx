import { HomeOutlined, TableOutlined } from '@ant-design/icons';
import HomePage from '@/pages/home';
import ReportsPage from '@/pages/reports';
import AppReport from '@/pages/engine';
import AppPreview from '@/pages/preview';
import React from "react";

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
