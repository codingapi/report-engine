import React, { useCallback, useState } from 'react';
import type { MessageInstance } from 'antd/es/message/interface';
import type { SheetPanelHandle } from '@/components/sheet-panel';
import type {
  CellBinding,
  LoopBlock,
  SummaryRow,
  ReportParam,
  Dataset,
  ReportConfig,
  ReportEngineProps,
} from '@/types';
import { valueDisplayText, templateToString } from '@/value-text';
import { summaryAxis, summaryHit, crossPosOf } from '@/utils/summary-axis';

export interface UseReportIOOptions {
  sheetRef: React.RefObject<SheetPanelHandle>;
  datasets: Dataset[];
  dataModelId?: string;
  cellBindings: CellBinding[];
  loopBlocks: LoopBlock[];
  summaries: SummaryRow[];
  params: ReportParam[];
  reportId: string | null;
  reportName: string;
  onReportIdChange: (id: string) => void;
  onSaveReport?: ReportEngineProps['onSaveReport'];
  onImport?: ReportEngineProps['onImport'];
  messageApi: MessageInstance;
}

/**
 * 报表 IO：保存配置 / 导入模板 / 收集渲染入参。
 * <p>从 ReportEngine 主组件抽出的 IO 边界。预览/导出的 UI 编排（参数弹窗、抽屉、反查）
 * 由 {@link useReportPreview} 承担，本 hook 只负责配置收集与保存/导入。
 */
export function useReportIO(opts: UseReportIOOptions) {
  const {
    sheetRef,
    datasets,
    dataModelId,
    cellBindings,
    loopBlocks,
    summaries,
    params,
    reportId,
    reportName,
    onReportIdChange,
    onSaveReport,
    onImport,
    messageApi,
  } = opts;

  const [savingReport, setSavingReport] = useState(false);
  const [importing, setImporting] = useState(false);

  /**
   * 收集渲染入参：绑定附带 preview 友好文本，模板用原始 ID（templateToString）替换友好别名，
   * 确保后端能正确解析 LoopFieldValue 等类型。导出/预览共用。返回 null 表示表格为空。
   */
  const collectRenderArgs = useCallback(() => {
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) return null;

    const bindingsOut = cellBindings.map((b) => ({
      ...b,
      preview: valueDisplayText(b.value, datasets, loopBlocks, params),
    }));
    const summariesOut = summaries.map((s) => ({
      ...s,
      cells: s.cells.map((c) => ({
        ...c,
        preview: valueDisplayText(c.value, datasets, loopBlocks, params),
      })),
    }));

    const templateOut = {
      ...snapshot,
      sheets: snapshot.sheets.map((sheet) => ({
        ...sheet,
        cells: sheet.cells.map((cell) => {
          const binding = cellBindings.find((b) => {
            const [, row, col] = b.cellKey.split(':');
            return parseInt(row) === cell.row && parseInt(col) === cell.col;
          });
          if (binding) {
            return { ...cell, value: templateToString(binding.value) };
          }
          const summary = summaries.find((s) => summaryHit(s, cell.row, cell.col));
          if (summary) {
            const cross = crossPosOf(summaryAxis(summary), cell.row, cell.col);
            const summaryCell = summary.cells.find((c) => c.crossPos === cross);
            if (summaryCell) {
              return { ...cell, value: templateToString(summaryCell.value) };
            }
          }
          return cell;
        }),
      })),
    };

    return { bindingsOut, summariesOut, templateOut };
  }, [cellBindings, loopBlocks, summaries, params, datasets, sheetRef]);

  // ─── 保存报表配置 ───
  const handleSaveReport = useCallback(async () => {
    if (!onSaveReport) return;
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) {
      messageApi.warning('表格为空，无法保存');
      return;
    }
    setSavingReport(true);
    try {
      // 重建模板：用原始 ID（templateToString）替换友好别名（valueDisplayText）
      // 确保后端能正确解析 LoopFieldValue 等类型
      const templateOut = {
        ...snapshot,
        sheets: snapshot.sheets.map((sheet) => ({
          ...sheet,
          cells: sheet.cells.map((cell) => {
            // 查找对应的 cellBinding
            const binding = cellBindings.find((b) => {
              const [, row, col] = b.cellKey.split(':');
              return parseInt(row) === cell.row && parseInt(col) === cell.col;
            });
            if (binding) {
              return { ...cell, value: templateToString(binding.value) };
            }
            // 查找对应的 summaryCell（按轴 + 交叉区间归属）
            const summary = summaries.find((s) => summaryHit(s, cell.row, cell.col));
            if (summary) {
              const cross = crossPosOf(summaryAxis(summary), cell.row, cell.col);
              const summaryCell = summary.cells.find((c) => c.crossPos === cross);
              if (summaryCell) {
                return { ...cell, value: templateToString(summaryCell.value) };
              }
            }
            // 其他单元格保持原值
            return cell;
          }),
        })),
      };

      // displayText 是设计态 transient 字段（回声判别/显示用），不持久化 → 保存前剥离
      const config: ReportConfig = {
        id: reportId ?? undefined,
        name: reportName,
        dataModelId,
        cellBindings: cellBindings.map(({ displayText: _dt, ...b }) => b),
        loopBlocks,
        summaries: summaries.map((s) => ({
          ...s,
          cells: s.cells.map(({ displayText: _dt, ...c }) => c),
        })),
        params,
        template: templateOut,
      };
      const id = await onSaveReport(config);
      if (id) onReportIdChange(id);
      messageApi.success('报表已保存');
    } catch (e) {
      messageApi.error(`保存失败: ${e}`);
    } finally {
      setSavingReport(false);
    }
  }, [
    onSaveReport,
    reportId,
    reportName,
    dataModelId,
    cellBindings,
    loopBlocks,
    summaries,
    params,
    onReportIdChange,
    sheetRef,
    messageApi,
  ]);

  // ─── 导入 ───
  const handleImport = useCallback(
    async (file: File) => {
      if (!onImport) return;
      setImporting(true);
      try {
        const snapshot = await onImport(file);
        sheetRef.current?.loadSnapshot(snapshot);
        messageApi.success('导入成功');
      } catch (e) {
        messageApi.error(`导入失败: ${e}`);
      } finally {
        setImporting(false);
      }
    },
    [onImport, sheetRef, messageApi],
  );

  return { savingReport, importing, collectRenderArgs, handleSaveReport, handleImport };
}
