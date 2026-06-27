import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, message, Spin } from 'antd';
import { CloseOutlined, PrinterOutlined } from '@ant-design/icons';
import type {
  Dataset,
  ExpressionCatalog,
  Relationship,
  ReportDTO,
  ReportEngineHandle,
} from '@coding-report/report-engine';
import { ReportEngine } from '@coding-report/report-engine';
import {
  type DataModelInfo,
  drillReport,
  fetchFonts,
  fetchFunctions,
  importExcel,
  loadReportConfig,
  previewReport,
  renderReport,
  saveReportConfig,
} from '@coding-report/report-api';

// ─── 页面组件 ──────────────────────────────────

/** 加载的报表配置（附带后端注入的数据模型信息） */
interface LoadedReportConfig extends ReportDTO {
  dataModel?: DataModelInfo;
}

const AppReport = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const reportIdFromUrl = searchParams.get('id');

  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [relationships, setRelationships] = useState<Relationship[]>([]);
  const [dataModelId, setDataModelId] = useState<string>('default');
  const [functions, setFunctions] = useState<ExpressionCatalog>();
  const [loading, setLoading] = useState(true);
  const engineRef = useRef<ReportEngineHandle>(null);

  // 加载公式目录
  useEffect(() => {
    fetchFunctions()
      .then(setFunctions)
      .catch((e) => console.error('加载公式列表失败:', e));
  }, []);

  // 根据 URL 参数加载报表；无 id 则回到报表管理页（创建动作由报表管理页承担）
  useEffect(() => {
    const init = async () => {
      if (!reportIdFromUrl) {
        navigate('/reports', { replace: true });
        return;
      }
      try {
        const config = await loadReportConfig<LoadedReportConfig>(reportIdFromUrl);
        const dm = config.dataModel;
        if (dm) {
          setDatasets(
            dm.datasets.map((d) => ({
              id: d.id,
              alias: d.alias || d.id,
              name: d.sourceTable ?? d.name,
              sourceType: d.dataSourceType || 'CSV',
              fields: d.fields.map((f) => ({
                name: f.name,
                alias: f.alias || f.name,
                dataType: f.dataType,
                primaryKey: f.primaryKey,
              })),
            })),
          );
          setRelationships(
            dm.relationships.map((r) => ({
              left: r.left,
              right: r.right,
              joinType: r.joinType,
            })),
          );
        }
        if (config.dataModelId) setDataModelId(config.dataModelId);
        engineRef.current?.loadReportConfig(config);
      } catch (e) {
        console.error('加载报表失败:', e);
      } finally {
        setLoading(false);
      }
    };
    init();
  }, [reportIdFromUrl, navigate]);

  const handleImport = async (file: File) => {
    return importExcel(file);
  };

  const handlePrintConfig = () => {
    const config = engineRef.current?.getReportConfig();
    if (!config) {
      message.warning('表格为空，无配置可打印');
      return;
    }
    console.log('[ReportDTO object]', config);
    console.log('[ReportDTO JSON]\n', JSON.stringify(config, null, 2));
  };

  const handleSaveReport = async (config: ReportDTO): Promise<string> => {
    return saveReportConfig({ ...config, dataModelId });
  };

  if (loading) {
    return (
      <div
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}
      >
        <Spin size="large" description="加载报表..." />
      </div>
    );
  }

  return (
    <ReportEngine
      datasets={datasets}
      relationships={relationships}
      dataModelId={dataModelId}
      functions={functions}
      engineRef={engineRef}
      renderService={{ preview: previewReport, export: renderReport, drill: drillReport }}
      onImport={handleImport}
      onSaveReport={handleSaveReport}
      onFontRequest={fetchFonts}
      customActions={
        <Button icon={<PrinterOutlined />} onClick={handlePrintConfig}>
          打印配置
        </Button>
      }
      extraActions={
        <Button icon={<CloseOutlined />} onClick={() => navigate('/reports', { replace: true })}>
          关闭
        </Button>
      }
    />
  );
};

export default AppReport;
