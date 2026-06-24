import {useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {Button, message, Space, Spin} from 'antd';
import {ArrowLeftOutlined} from '@ant-design/icons';
import type {
    CellBinding,
    LoopBlock,
    RenderConfig,
    ReportConfig,
    ReportParam,
    SummaryRow,
} from '@coding-report/report-engine';
import {ReportPreview} from '@coding-report/report-engine';
import type {ExcelWorkbook} from '@coding-report/report-univer';
import {
    type DataModelInfo,
    drillReport,
    loadReportConfig,
    previewReport,
    renderReport,
} from '@coding-report/report-api';

/** 加载的报表配置（附带后端注入的数据模型信息） */
interface LoadedReportConfig extends ReportConfig {
    dataModel?: DataModelInfo;
}

const AppPreview = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const reportId = searchParams.get('id');

    const [loading, setLoading] = useState(true);
    const [reportName, setReportName] = useState<string>('报表预览');
    const [previewConfig, setPreviewConfig] = useState<RenderConfig | null>(null);

    // 加载报表配置：成功后交给 <ReportPreview> 预览（config 引用变化即触发）
    useEffect(() => {
        let active = true;
        if (!reportId) {
            setLoading(false);
            return;
        }
        (async () => {
            try {
                const config = await loadReportConfig<LoadedReportConfig>(reportId);
                if (!active) return;
                if (config.name) setReportName(config.name);
                setPreviewConfig({
                    bindings: config.cellBindings as CellBinding[],
                    loops: config.loopBlocks as LoopBlock[],
                    summaries: config.summaries as SummaryRow[],
                    workbook: config.template as ExcelWorkbook,
                    params: (config.params as ReportParam[]) ?? [],
                });
            } catch (e) {
                message.error(`加载报表失败: ${e}`);
            } finally {
                if (active) setLoading(false);
            }
        })();
        return () => {
            active = false;
        };
    }, [reportId]);

    if (loading) {
        return (
            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh'}}>
                <Spin size="large" description="加载报表..."/>
            </div>
        );
    }

    return (
        <>
            <div style={{padding: '16px 24px', borderBottom: '1px solid #f0f0f0'}}>
                <Space>
                    <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/reports')}>
                        返回报表管理
                    </Button>
                    <h3 style={{margin: 0}}>{reportName}</h3>
                </Space>
            </div>
            <ReportPreview
                renderService={{preview: previewReport, export: renderReport, drill: drillReport}}
                config={previewConfig}
                onClose={() => navigate('/reports')}
            />
        </>
    );
};

export default AppPreview;
