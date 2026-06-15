import { HomeOutlined, TableOutlined } from '@ant-design/icons';
import HomePage from '@/pages/home';
import EnginePage from '@/pages/engine';
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
    name: '报表设计器',
    icon: <TableOutlined />,
    element: <EnginePage />,
  },
];

export default routes;
