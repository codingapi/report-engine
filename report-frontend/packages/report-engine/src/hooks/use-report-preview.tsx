import { useRef, useState } from 'react';
import type { MessageInstance } from 'antd/es/message/interface';
import type { DrillResult, PreviewResult, RenderRequest } from '@coding-report/report-api';
import type { ExcelWorkbook } from '@coding-report/report-univer';
import type { ParamDTO, RenderConfig, RenderService } from '@/types';
import { toBindingDTO } from '@/utils/render-dto';

export interface UseReportPreviewOptions {
  renderService: RenderService;
  messageApi: MessageInstance;
  /** 预览抽屉关闭回调：设计器可不传（仅关抽屉），独立预览页可借此返回上一页。 */
  onClose?: () => void;
}

/** 挂起的渲染动作：等参数弹窗确认后再执行。 */
interface PendingRender {
  mode: 'export' | 'preview';
  config: RenderConfig;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/** 合并默认值与用户输入，生成参数值映射 */
function buildParamValues(params: ParamDTO[], userInput: Record<string, unknown>) {
  return Object.fromEntries(
    params.map((p) => [p.name, userInput[p.name] ?? p.defaultValue ?? null]),
  );
}

/**
 * 报表预览能力（纯逻辑/状态）：参数弹窗 → 渲染 → 预览抽屉 → 反查 → 抽屉内导出。
 * <p>由 app 层注入 {@link RenderService}（桥接 report-api），report-engine 不直接调 API。
 * 视图层（{@code ReportPreview} 组件）根据本 hook 返回的状态渲染 ParamInputModal / 预览抽屉 / DrillModal。
 */
export function useReportPreview({ renderService, messageApi, onClose }: UseReportPreviewOptions) {
  const [previewing, setPreviewing] = useState(false);
  const [exporting, setExporting] = useState(false);

  // 参数弹窗状态
  const [paramModalOpen, setParamModalOpen] = useState(false);
  const [pendingConfig, setPendingConfig] = useState<RenderConfig | null>(null);
  const pendingRef = useRef<PendingRender | null>(null);

  // 预览抽屉状态
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewWorkbook, setPreviewWorkbook] = useState<ExcelWorkbook>();
  const [previewDrillable, setPreviewDrillable] = useState<string[]>([]);
  const [exportingPreview, setExportingPreview] = useState(false);
  // 预览所用的渲染请求：抽屉内「导出报表」/反查复用同一份配置/参数，无需重新填参
  const previewRequestRef = useRef<RenderRequest | null>(null);

  // 反查明细弹窗状态
  const [drillModalOpen, setDrillModalOpen] = useState(false);
  const [drillResult, setDrillResult] = useState<DrillResult>();
  const [drillLoading, setDrillLoading] = useState(false);

  /** 由 RenderConfig + 参数值构建后端渲染请求 */
  const buildRequest = (
    config: RenderConfig,
    paramValues: Record<string, unknown>,
  ): RenderRequest => ({
    dataModelId: config.dataModelId ?? 'default',
    cellBindings: config.bindings.map(toBindingDTO),
    loopBlocks: config.loops as unknown[],
    summaries: config.summaries as unknown[],
    params: paramValues,
    template: config.workbook,
  });

  /** 执行渲染：按 pending.mode 决定下载 .xlsx 或网页预览 */
  const doRender = async (paramValues: Record<string, unknown>) => {
    const p = pendingRef.current;
    if (!p) return;
    pendingRef.current = null;
    setPendingConfig(null);
    const request = buildRequest(p.config, paramValues);
    if (p.mode === 'preview') {
      const result: PreviewResult = await renderService.preview(request);
      previewRequestRef.current = request; // 抽屉内导出/反查复用
      setPreviewWorkbook(result.workbook);
      setPreviewDrillable(result.drillable || []);
      setPreviewOpen(true);
    } else {
      const blob = await renderService.export(request);
      downloadBlob(blob, `report-${new Date().toISOString().slice(0, 10)}.xlsx`);
    }
  };

  /** 预览抽屉内导出：复用生成预览时的同一份请求，直接渲染下载 */
  const exportFromPreview = async () => {
    const req = previewRequestRef.current;
    if (!req) return;
    setExportingPreview(true);
    try {
      const blob = await renderService.export(req);
      downloadBlob(blob, `report-${new Date().toISOString().slice(0, 10)}.xlsx`);
    } catch (e) {
      messageApi.error(`导出失败: ${e}`);
    } finally {
      setExportingPreview(false);
    }
  };

  /** 反查明细：点击预览中的可反查格 → 调 renderService.drill → 弹窗展示原始行 */
  const drill = async (row: number, col: number) => {
    const req = previewRequestRef.current;
    if (!req) return;
    setDrillResult(undefined);
    setDrillModalOpen(true);
    setDrillLoading(true);
    try {
      const result = await renderService.drill({ request: req, row, col });
      setDrillResult(result);
    } catch (e) {
      messageApi.error(`反查失败: ${e}`);
      setDrillModalOpen(false);
    } finally {
      setDrillLoading(false);
    }
  };

  /** 预览/导出共用：收集必填参数（弹窗）后执行渲染动作 */
  const runRenderAction = async (mode: 'export' | 'preview', config: RenderConfig) => {
    pendingRef.current = { mode, config };
    setPendingConfig(config);
    const { params } = config;
    // 有无默认值的必填参数 → 弹窗输入
    const hasRequired = params.some((p) => !p.defaultValue);
    if (hasRequired) {
      setParamModalOpen(true);
      return;
    }
    // 全部有默认值或无参数 → 直接渲染
    await doRender(buildParamValues(params, {}));
  };

  const openPreview = async (config: RenderConfig) => {
    setPreviewing(true);
    try {
      await runRenderAction('preview', config);
    } catch (e) {
      messageApi.error(`预览失败: ${e}`);
    } finally {
      setPreviewing(false);
    }
  };

  const openExport = async (config: RenderConfig) => {
    setExporting(true);
    try {
      await runRenderAction('export', config);
      messageApi.success('导出成功');
    } catch (e) {
      messageApi.error(`导出失败: ${e}`);
    } finally {
      setExporting(false);
    }
  };

  const closePreview = () => {
    setPreviewOpen(false);
    onClose?.();
  };

  const confirmParams = async (values: Record<string, unknown>) => {
    setParamModalOpen(false);
    const params = pendingRef.current?.config.params ?? [];
    await doRender(buildParamValues(params, values));
  };

  const cancelParams = () => {
    setParamModalOpen(false);
    pendingRef.current = null;
    setPendingConfig(null);
  };

  const closeDrill = () => setDrillModalOpen(false);

  return {
    openPreview,
    openExport,
    previewing,
    exporting,
    // 预览抽屉
    previewOpen,
    previewWorkbook,
    previewDrillable,
    exportingPreview,
    closePreview,
    exportFromPreview,
    drill,
    // 参数弹窗
    paramModalOpen,
    pendingParams: pendingConfig?.params ?? [],
    confirmParams,
    cancelParams,
    // 反查弹窗
    drillModalOpen,
    drillResult,
    drillLoading,
    closeDrill,
  };
}
