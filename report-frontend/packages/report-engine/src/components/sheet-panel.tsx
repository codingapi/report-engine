import React, { forwardRef, useImperativeHandle, useRef } from 'react';
import {
  UniverSheet,
  type UniverSheetHandle,
  type UniverSheetProps,
  type SelectedCellInfo,
  type CellHandle,
  type FieldDropInfo,
  type CellProp,
  type LoopBlockConfig,
  type CellRange,
  type FontItem,
  type ExcelWorkbook,
} from '@coding-report/report-univer';

export interface SheetPanelHandle {
  getSnapshot: () => ExcelWorkbook | null;
  loadSnapshot: (snapshot: ExcelWorkbook) => void;
  setCellValue: (sheetId: string, row: number, column: number, value: string) => void;
  /** 获取当前活动工作表的实际 ID */
  getActiveSheetId: () => string;
}

export interface SheetCellSelectInfo {
  info: SelectedCellInfo;
  cellProps: CellProp[] | undefined;
}

interface SheetPanelProps {
  cellProps?: Record<string, CellProp[]>;
  loopBlocks?: Record<string, LoopBlockConfig>;
  highlightCells?: CellRange[];
  onCellSelect?: (info: SheetCellSelectInfo) => void;
  onFieldDrop?: (info: FieldDropInfo, handle: CellHandle) => void;
  onFontRequest?: () => Promise<FontItem[]>;
  onReady?: () => void;
}

/**
 * Univer 电子表格封装：转发 ref 句柄，简化 props 传递。
 */
const SheetPanel = forwardRef<SheetPanelHandle, SheetPanelProps>(
  ({ cellProps, loopBlocks, highlightCells, onCellSelect, onFieldDrop, onFontRequest, onReady }, ref) => {
    const univerRef = useRef<UniverSheetHandle>(null);

    useImperativeHandle(ref, () => ({
      getSnapshot: () => univerRef.current?.getSnapshot() ?? null,
      loadSnapshot: (snapshot) => {
        univerRef.current?.loadSnapshot(snapshot);
      },
      setCellValue: (sheetId, row, column, value) => {
        univerRef.current?.setCellValue(sheetId, row, column, value);
      },
      getActiveSheetId: () => {
        const snap = univerRef.current?.getSnapshot();
        if (snap?.sheets?.length) return snap.sheets[0].id || 'sheet1';
        return 'sheet1';
      },
    }));

    const handleCellSelect: UniverSheetProps['onCellSelect'] = (info, _handle, props) => {
      onCellSelect?.({ info, cellProps: props });
    };

    return (
      <UniverSheet
        ref={univerRef}
        style={{ height: '100%' }}
        cellProps={cellProps}
        loopBlocks={loopBlocks}
        highlightCells={highlightCells}
        onCellSelect={handleCellSelect}
        onFieldDrop={onFieldDrop}
        onFontRequest={onFontRequest}
        onReady={onReady}
      />
    );
  },
);

SheetPanel.displayName = 'SheetPanel';

export default SheetPanel;
