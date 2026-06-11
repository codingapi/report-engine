import { Typography } from 'antd';

const { Title } = Typography;

const TestPage = () => {
  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>测试界面</Title>
      <Typography.Text>此页面用于功能测试。</Typography.Text>
    </div>
  );
};

export default TestPage;
