import { ReportEngine } from '@coding-report/report-engine';
import { mockDataConfig } from '@/data/mock-data';

const HomePage = () => {
  return <ReportEngine dataConfig={mockDataConfig} title="销售数据月报" />;
};

export default HomePage;
