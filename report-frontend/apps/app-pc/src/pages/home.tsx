import { useNavigate } from 'react-router-dom';
import { Menu, Typography, Card } from 'antd';
import {menuItems} from "@/config/menus.tsx";

const { Title, Text } = Typography;

const HomePage = () => {
  const navigate = useNavigate();

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  return (
    <div style={{ maxWidth: 600, margin: '80px auto', padding: '0 24px' }}>
      <Title level={2} style={{ textAlign: 'center', marginBottom: 8 }}>
        Report Engine
      </Title>
      <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginBottom: 32 }}>
        基于 Univer 的报表引擎演示平台
      </Text>
      <Card>
        <Menu
          mode="inline"
          items={menuItems}
          onClick={handleMenuClick}
          defaultSelectedKeys={['/']}
          style={{ border: 'none' }}
        />
      </Card>
    </div>
  );
};

export default HomePage;
