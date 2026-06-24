import React, { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { Button, Drawer, Space, message } from 'antd';
import { CloseOutlined, ExportOutlined } from '@ant-design/icons';
import type { RenderConfig, RenderService } from '@/types';
import ParamInputModal from '@/components/param-input-modal';
import DrillModal from './drill-modal';
import WorkbookTable from './workbook-table';
import { useReportPreview } from '@/hooks/use-report-preview';

export interface ReportPreviewHandle {
  /** 直接导出当前配置为 .xlsx（不经预览抽屉），返回 Promise 供调用方追踪 loading */
  exportXlsx: (config: RenderConfig) => Promise<void>;
}

export interface ReportPreviewProps {
  /** 渲染服务（桥接 report-api），由 app 注入 */
  renderService: RenderService;
  /**
   * 传入配置即触发网页预览：引用变化时重新预览。
   * 设计器点「预览」按钮传入当前编辑器配置；独立预览页加载配置后传入。
   */
  config?: RenderConfig | null;
  /** 预览 loading 变化回调（供调用方驱动按钮 loading 状态） */
  onPreviewingChange?: (loading: boolean) => void;
  /** 导出 loading 变化回调 */
  onExportingChange?: (loading: boolean) => void;
  /** 预览抽屉关闭回调：设计器可不传（仅关抽屉），独立预览页可借此返回上一页。 */
  onClose?: () => void;
}

/**
 * 报表预览能力组件：参数弹窗 → 渲染 → 预览抽屉 → 反查 → 抽屉内导出。
 * <p>由 report-engine 提供，设计器（报表配置界面）与独立预览页共用：
 * <ul>
 *   <li>声明式：传入 {@code config}（引用变化）即触发预览。</li>
 *   <li>命令式：通过 ref 调 {@link ReportPreviewHandle#exportXlsx} 直接导出。</li>
 * </ul>
 * 渲染函数由 app 注入（report-engine 不直接调 API）。
 * <p>预览抽屉内部用私有 {@link WorkbookTable} 把渲染后的工作簿画成 HTML 表格。
 */
const ReportPreview = forwardRef<ReportPreviewHandle, ReportPreviewProps>(
  ({ renderService, config, onPreviewingChange, onExportingChange, onClose }, ref) => {
    const [messageApi, messageContextHolder] = message.useMessage();
    const {
      openPreview,
      openExport,
      previewing,
      exporting,
      previewOpen,
      previewWorkbook,
      previewDrillable,
      exportingPreview,
      closePreview,
      exportFromPreview,
      drill,
      paramModalOpen,
      pendingParams,
      confirmParams,
      cancelParams,
      drillModalOpen,
      drillResult,
      drillLoading,
      closeDrill,
    } = useReportPreview({ renderService, messageApi, onClose });

    const lastPreviewedRef = useRef<RenderConfig | null>(null);

    // config 引用变化时触发预览（避免 strict mode 重复触发）
    useEffect(() => {
      if (!config || config === lastPreviewedRef.current) return;
      lastPreviewedRef.current = config;
      openPreview(config);
    }, [config, openPreview]);

    useEffect(() => {
      onPreviewingChange?.(previewing);
    }, [previewing, onPreviewingChange]);
    useEffect(() => {
      onExportingChange?.(exporting);
    }, [exporting, onExportingChange]);

    useImperativeHandle(
      ref,
      () => ({
        exportXlsx: (cfg: RenderConfig) => openExport(cfg),
      }),
      [openExport],
    );

    return (
      <>
        {messageContextHolder}

        <ParamInputModal
          params={pendingParams}
          open={paramModalOpen}
          onConfirm={confirmParams}
          onCancel={cancelParams}
        />

        <Drawer
          title="报表预览"
          placement="right"
          width="100%"
          open={previewOpen}
          onClose={closePreview}
          closable={false}
          destroyOnHidden
          styles={{ body: { padding: 0, background: '#fff' } }}
          extra={
            <Space>
              <Button
                type="primary"
                icon={<ExportOutlined />}
                loading={exportingPreview}
                onClick={exportFromPreview}
              >
                导出报表
              </Button>
              <Button icon={<CloseOutlined />} onClick={closePreview}>
                关闭
              </Button>
            </Space>
          }
        >
          <WorkbookTable workbook={previewWorkbook} drillable={previewDrillable} onDrill={drill} />
        </Drawer>

        <DrillModal
          open={drillModalOpen}
          loading={drillLoading}
          result={drillResult}
          onClose={closeDrill}
        />
      </>
    );
  },
);

ReportPreview.displayName = 'ReportPreview';

export default ReportPreview;
