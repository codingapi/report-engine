import { ReportEngine } from '@coding-report/report-engine';
import { mockDataConfig } from '@/data/mock-data';

const EnginePage = () => {
  return <ReportEngine dataConfig={mockDataConfig} title="销售数据月报" />;
};

export default EnginePage;
