import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Menu, Typography, Card, List, Spin } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import { listExampleReports } from '@coding-report/report-api';
import type { ReportBrief } from '@coding-report/report-api';
import { menuItems } from '@/config/menus.tsx';

const { Title, Text } = Typography;

const HomePage = () => {
  const navigate = useNavigate();
  const [examples, setExamples] = useState<ReportBrief[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listExampleReports()
      .then(setExamples)
      .catch((e) => console.error('加载示例报表失败:', e))
      .finally(() => setLoading(false));
  }, []);

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

      <Title level={4} style={{ marginTop: 32, marginBottom: 12 }}>
        <FileTextOutlined /> 示例报表
      </Title>
      {loading ? (
        <Spin />
      ) : (
        <Card>
          <List
            dataSource={examples}
            renderItem={(item) => (
              <List.Item
                style={{ cursor: 'pointer' }}
                onClick={() => navigate(`/engine?id=${item.id}`)}
              >
                <List.Item.Meta title={item.name} />
              </List.Item>
            )}
            locale={{ emptyText: '暂无示例报表' }}
          />
        </Card>
      )}
    </div>
  );
};

export default HomePage;
