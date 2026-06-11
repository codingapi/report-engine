import { HomeOutlined, TableOutlined, ExperimentOutlined, ApiOutlined } from '@ant-design/icons';

export  const menuItems = [
    { key: '/', icon: <HomeOutlined />, label: '首页' },
    { key: '/engine', icon: <TableOutlined />, label: '引擎界面' },
    { key: '/test', icon: <ExperimentOutlined />, label: '测试界面' },
    { key: '/univer-test', icon: <ApiOutlined />, label: 'Univer 验证' },
];