import { HomeOutlined, TableOutlined, ExperimentOutlined } from '@ant-design/icons';
import HomePage from '@/pages/home';
import EnginePage from '@/pages/engine';
import TestPage from '@/pages/test';
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
    path: '/engine',
    name: '引擎界面',
    icon: <TableOutlined />,
    element: <EnginePage />,
  },
  {
    path: '/test',
    name: '测试界面',
    icon: <ExperimentOutlined />,
    element: <TestPage />,
  },
];

export default routes;
