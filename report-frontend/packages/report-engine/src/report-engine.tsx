import React, { useState, useCallback, useRef } from 'react';
import { Button, Upload, message } from 'antd';
import {
  ExportOutlined,
  ImportOutlined,
} from '@ant-design/icons';
import { Group, Panel as ResizablePanel } from 'react-resizable-panels';
import type { CellProp, FieldDropInfo, CellHandle, LoopBlockConfig } from '@coding-report/report-univer';
import DatasetTree from './components/dataset-tree';
import SheetPanel from './components/sheet-panel';
import type { SheetPanelHandle, SheetCellSelectInfo } from './components/sheet-panel';
import PropertyPanel from './components/property-panel';
import type { ReportEngineProps, CellBinding, LoopBlock, SummaryRow, Dataset } from './types';
import './index.css';

/**
 * 报表设计器：三栏布局（数据集树 + 电子表格 + 属性面板）。
 */
export const ReportEngine: React.FC<ReportEngineProps> = ({
  datasets,
  title = '报表设计器',
  onExport,
  onImport,
  onFontRequest,
}) => {
  const sheetRef = useRef<SheetPanelHandle>(null);
  const [messageApi, messageContextHolder] = message.useMessage();

  // ─── 状态 ───
  const [selectedCell, setSelectedCell] = useState<SheetCellSelectInfo | null>(null);
  const [cellBindings, setCellBindings] = useState<CellBinding[]>([]);
  const [loopBlocks] = useState<Record<string, LoopBlockConfig>>({});
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);

  // ─── cellProps：CellBinding → Univer CellProp 映射 ───
  const cellProps: Record<string, CellProp[]> = {};
  for (const b of cellBindings) {
    cellProps[b.cellKey] = [{
      kind: 'cellBinding',
      field: b.value.payload,
      data: {
        valueType: b.value.type,
        expansion: b.expansion,
        expandMode: b.expandMode,
        mergeRepeated: b.mergeRepeated,
        parentCell: b.parentCell,
        conditionsCount: b.conditions.length,
      },
    }];
  }

  // ─── 单元格选中 ───
  const handleCellSelect = useCallback((info: SheetCellSelectInfo) => {
    setSelectedCell(info);
  }, []);

  // ─── 字段拖入 → 创建 CellBinding ───
  const handleFieldDrop = useCallback(
    (info: FieldDropInfo, handle: CellHandle) => {
      try {
        const data = JSON.parse(info.data);
        if (data.datasetId && data.field) {
          const cellKey = `${info.sheetId}:${info.row}:${info.column}`;
          const fieldRef = `${data.datasetId}.${data.field}`;

          // 设置单元格显示文本
          handle.setValue(data.alias || data.field);

          // 创建绑定
          const binding: CellBinding = {
            cellKey,
            value: { type: 'FieldValue', payload: fieldRef },
            expansion: 'VERTICAL',
            expandMode: 'LIST',
            mergeRepeated: false,
            parentCell: null,
            conditions: [],
          };

          setCellBindings((prev) => {
            const filtered = prev.filter((b) => b.cellKey !== cellKey);
            return [...filtered, binding];
          });

          messageApi.success(`已绑定 ${data.alias || data.field}`);
        }
      } catch {
        // 非 JSON 拖拽数据，忽略
      }
    },
    [messageApi],
  );

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
      const loops: LoopBlock[] = [];
      const summaries: SummaryRow[] = [];
      await onExport(cellBindings, loops, summaries, snapshot);
      messageApi.success('导出成功');
    } catch (e) {
      messageApi.error(`导出失败: ${e}`);
    } finally {
      setExporting(false);
    }
  }, [onExport, cellBindings, messageApi]);

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
    [onImport, messageApi],
  );

  return (
    <div className="re">
      {messageContextHolder}

      {/* 顶栏 */}
      <div className="re-header">
        <div className="re-header__title">{title}</div>
        <div className="re-header__actions">
          {onImport && (
            <Upload
              accept=".xlsx,.xls"
              showUploadList={false}
              beforeUpload={(file) => {
                handleImport(file);
                return false;
              }}
            >
              <Button icon={<ImportOutlined />} loading={importing}>
                导入
              </Button>
            </Upload>
          )}
          {onExport && (
            <Button
              type="primary"
              icon={<ExportOutlined />}
              loading={exporting}
              onClick={handleExport}
            >
              导出
            </Button>
          )}
        </div>
      </div>

      {/* 三栏主体 */}
      <div className="re-body">
        <Group orientation="horizontal">
          <ResizablePanel defaultSize="15%" minSize="200px" maxSize="25%">
            <DatasetTree datasets={datasets} />
          </ResizablePanel>

          <ResizablePanel defaultSize="70%">
            <SheetPanel
              ref={sheetRef}
              cellProps={cellProps}
              loopBlocks={loopBlocks}
              onCellSelect={handleCellSelect}
              onFieldDrop={handleFieldDrop}
              onFontRequest={onFontRequest}
            />
          </ResizablePanel>

          <ResizablePanel defaultSize="15%" minSize="200px" maxSize="25%">
            <PropertyPanel
              selectedCell={selectedCell}
              cellBindings={cellBindings}
              datasets={datasets}
            />
          </ResizablePanel>
        </Group>
      </div>
    </div>
  );
};
