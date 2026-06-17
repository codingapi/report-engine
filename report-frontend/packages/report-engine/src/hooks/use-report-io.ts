import React, { useCallback, useState } from 'react';
import type { MessageInstance } from 'antd/es/message/interface';
import type { SheetPanelHandle } from '../components/sheet-panel';
import type { CellBinding, LoopBlock, SummaryRow, ReportParam, Dataset, ReportConfig, ReportEngineProps } from '../types';
import { valueDisplayText, templateToString } from '../value-text';

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
  onExport?: ReportEngineProps['onExport'];
  onImport?: ReportEngineProps['onImport'];
  messageApi: MessageInstance;
}

/**
 * 报表 IO：保存配置 / 导出渲染 / 导入模板。
 * <p>从 ReportEngine 主组件抽出的 IO 边界——逻辑逐字保留，仅集中管理三个 loading 状态
 * 与对外回调的调用。配置/快照由主组件通过 opts 传入。
 */
export function useReportIO(opts: UseReportIOOptions) {
  const {
    sheetRef, datasets, dataModelId,
    cellBindings, loopBlocks, summaries, params,
    reportId, reportName, onReportIdChange,
    onSaveReport, onExport, onImport, messageApi,
  } = opts;

  const [savingReport, setSavingReport] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);

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
            // 查找对应的 summaryCell
            const summary = summaries.find((s) => s.row === cell.row);
            if (summary) {
              const summaryCell = summary.cells.find((c) => c.column === cell.col);
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
  }, [onSaveReport, reportId, reportName, dataModelId, cellBindings, loopBlocks, summaries, params, onReportIdChange, sheetRef, messageApi]);

  // ─── 导出 ───
  const handleExport = useCallback(async () => {
    if (!onExport) return;
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) {
      messageApi.warning('表格为空，无法导出');
      return;
    }
    setExporting(true);
    try {
      // 导出时附带表达式预览（友好文本），随配置一起存储到后端
      const bindingsOut = cellBindings.map((b) => ({
        ...b,
        preview: valueDisplayText(b.value, datasets, loopBlocks, params),
      }));
      const summariesOut = summaries.map((s) => ({
        ...s,
        cells: s.cells.map((c) => ({ ...c, preview: valueDisplayText(c.value, datasets, loopBlocks, params) })),
      }));

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
            // 查找对应的 summaryCell
            const summary = summaries.find((s) => s.row === cell.row);
            if (summary) {
              const summaryCell = summary.cells.find((c) => c.column === cell.col);
              if (summaryCell) {
                return { ...cell, value: templateToString(summaryCell.value) };
              }
            }
            // 其他单元格保持原值
            return cell;
          }),
        })),
      };

      await onExport(bindingsOut, loopBlocks, summariesOut, templateOut, params);
      messageApi.success('导出成功');
    } catch (e) {
      messageApi.error(`导出失败: ${e}`);
    } finally {
      setExporting(false);
    }
  }, [onExport, cellBindings, loopBlocks, summaries, params, datasets, sheetRef, messageApi]);

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

  return { savingReport, exporting, importing, handleSaveReport, handleExport, handleImport };
}
