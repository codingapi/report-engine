import React, { useEffect, useRef } from 'react';
import { BehaviorSubject } from 'rxjs';
import { MessageType } from '@univerjs/design';
import type { FUniver } from '@univerjs/presets';
import {UniverSheet} from '@coding-report/report-univer';
import type { SelectedCellInfo, LoopBlockConfig } from '../properties/types';

export interface SheetPanelProps {
    /** 单元格选中回调 */
    onCellSelect?: (info: SelectedCellInfo) => void;
    /** 创建循环块回调 */
    onCreateLoopBlock?: (range: { sheetId: string; startRow: number; startColumn: number; endRow: number; endColumn: number }) => void;
    /** 编辑循环块回调 */
    onEditLoopBlock?: (id: string) => void;
    /** 移除循环块回调 */
    onRemoveLoopBlock?: (id: string) => void;
    /** 循环块数据 */
    loopBlocks?: Record<string, LoopBlockConfig>;
}

/** 在 loopBlocks 中查找包含指定行列的循环块 */
function findBlockAtCell(
    blocks: Record<string, LoopBlockConfig>,
    sheetId: string,
    row: number,
    col: number,
): LoopBlockConfig | null {
    for (const block of Object.values(blocks)) {
        if (
            block.sheetId === sheetId &&
            row >= block.startRow &&
            row <= block.endRow &&
            col >= block.startColumn &&
            col <= block.endColumn
        ) {
            return block;
        }
    }
    return null;
}

/** 给 FMenu 注入 disabled$ Observable（利用 FMenu 内部 _buildingSchema 结构） */
function injectDisabled(menu: any, disabled$: BehaviorSubject<boolean>) {
    const originalFactory = menu._buildingSchema.menuItemFactory;
    menu._buildingSchema.menuItemFactory = (accessor: any) => ({
        ...originalFactory(accessor),
        disabled$,
    });
}

const SheetPanel: React.FC<SheetPanelProps> = ({
    onCellSelect,
    onCreateLoopBlock,
    onEditLoopBlock,
    onRemoveLoopBlock,
    loopBlocks = {},
}) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const univerAPIRef = useRef<FUniver | null>(null);
    const prevLoopBlocksRef = useRef<Record<string, LoopBlockConfig>>({});

    // 用 ref 保存最新回调和数据，避免 onCreate 闭包捕获过期值
    const loopBlocksRef = useRef(loopBlocks);
    const onCreateLoopBlockRef = useRef(onCreateLoopBlock);
    const onEditLoopBlockRef = useRef(onEditLoopBlock);
    const onRemoveLoopBlockRef = useRef(onRemoveLoopBlock);
    onCreateLoopBlockRef.current = onCreateLoopBlock;
    onEditLoopBlockRef.current = onEditLoopBlock;
    onRemoveLoopBlockRef.current = onRemoveLoopBlock;

    // 循环块菜单 disabled 状态（模块级 BehaviorSubject）
    const setDisabled$ = useRef(new BehaviorSubject<boolean>(false));
    const editDisabled$ = useRef(new BehaviorSubject<boolean>(true));
    const removeDisabled$ = useRef(new BehaviorSubject<boolean>(true));

    // 更新循环块菜单的 disabled 状态
    const updateLoopBlockMenuState = (sheetId: string, row: number, col: number) => {
        const block = findBlockAtCell(loopBlocksRef.current, sheetId, row, col);
        const hasBlock = block !== null;
        setDisabled$.current.next(hasBlock);
        editDisabled$.current.next(!hasBlock);
        removeDisabled$.current.next(!hasBlock);
    };

    // ===== 通过 Univer 原生 API 管理循环块虚线边框 =====
    useEffect(() => {
        const api = univerAPIRef.current;
        if (!api) return;

        const workbook = api.getActiveWorkbook();
        if (!workbook) return;

        // 清除旧边框
        for (const block of Object.values(prevLoopBlocksRef.current)) {
            try {
                const sheet = workbook.getSheetBySheetId(block.sheetId);
                if (!sheet) continue;
                const numRows = block.endRow - block.startRow + 1;
                const numCols = block.endColumn - block.startColumn + 1;
                const range = sheet.getRange(block.startRow, block.startColumn, numRows, numCols);
                range.setBorder(api.Enum.BorderType.OUTSIDE, api.Enum.BorderStyleTypes.NONE);
            } catch {
                // 工作表可能已被删除，忽略
            }
        }

        // 设置新边框
        for (const block of Object.values(loopBlocks)) {
            try {
                const sheet = workbook.getSheetBySheetId(block.sheetId);
                if (!sheet) continue;
                const numRows = block.endRow - block.startRow + 1;
                const numCols = block.endColumn - block.startColumn + 1;
                const range = sheet.getRange(block.startRow, block.startColumn, numRows, numCols);
                range.setBorder(api.Enum.BorderType.OUTSIDE, api.Enum.BorderStyleTypes.DASHED, '#1677ff');
            } catch {
                // 渲染单元尚未就绪，忽略
            }
        }

        prevLoopBlocksRef.current = loopBlocks;
        loopBlocksRef.current = loopBlocks;
    }, [loopBlocks]);

    return (
        <div
            ref={containerRef}
            style={{ height: '100%', position: 'relative' }}
        >
            <UniverSheet
                style={{ height: '100%' }}
                onCreate={(_univer, univerAPI, _container) => {
                    univerAPIRef.current = univerAPI;

                    // ========== 单元格选中事件 ==========
                    univerAPI.addEvent(univerAPI.Event.SelectionChanged, (params) => {
                        const { worksheet: ws, selections } = params;
                        if (selections.length === 0) return;

                        const primary = selections[0];
                        const row = primary.startRow;
                        const col = primary.startColumn;
                        const sheetId = (ws as any).getSheetId();

                        // 更新循环块菜单 disabled 状态
                        updateLoopBlockMenuState(sheetId, row, col);

                        // 剥离公式前缀：将 = 开头的值转为纯文本
                        const rawCell = (ws as any).getCellRaw?.(row, col);
                        if (rawCell && typeof rawCell.f === 'string' && rawCell.f.startsWith('=')) {
                            const plainText = rawCell.f.slice(1);
                            ws.getRange(row, col).setValue(plainText);
                        }

                        if (!onCellSelect) return;

                        const range = ws.getRange(row, col);
                        const a1Notation = range?.getA1Notation() ?? '';
                        const value = range?.getValue() ?? null;

                        const mergeRange = (ws as any).getCellMergeData?.(row, col);
                        let mergeInfo: SelectedCellInfo['mergeRange'] = null;

                        if (mergeRange) {
                            const mergeStartRow = mergeRange.getRow();
                            const mergeStartCol = mergeRange.getColumn();
                            const mergeEndRow = mergeStartRow + mergeRange.getHeight() - 1;
                            const mergeEndCol = mergeStartCol + mergeRange.getWidth() - 1;
                            mergeInfo = {
                                startRow: mergeStartRow,
                                startColumn: mergeStartCol,
                                endRow: mergeEndRow,
                                endColumn: mergeEndCol,
                                a1Notation: mergeRange.getA1Notation(),
                            };
                        }

                        onCellSelect({
                            sheetId,
                            row,
                            column: col,
                            a1Notation,
                            value,
                            mergeRange: mergeInfo,
                        });
                    });

                    // ========== 右键菜单 ==========

                    // ---------- 循环块二级菜单 ----------

                    const setMenu = univerAPI.createMenu({
                        id: 'loop-block-set',
                        title: '设置',
                        tooltip: '将选中区域设置为循环块',
                        action: () => {
                            const callback = onCreateLoopBlockRef.current;
                            if (!callback) return;

                            const workbook = univerAPI.getActiveWorkbook();
                            const sheet = workbook?.getActiveSheet();
                            if (!sheet) return;

                            const activeRange = sheet.getActiveRange();
                            if (!activeRange) return;

                            const width = activeRange.getWidth();
                            const height = activeRange.getHeight();

                            if (width <= 1 && height <= 1) {
                                univerAPI.showMessage({
                                    content: '请先选择多个单元格再设置循环块',
                                    type: MessageType.Warning,
                                    duration: 2000,
                                });
                                return;
                            }

                            const sheetId = (sheet as any).getSheetId();
                            callback({
                                sheetId,
                                startRow: activeRange.getRow(),
                                startColumn: activeRange.getColumn(),
                                endRow: activeRange.getRow() + height - 1,
                                endColumn: activeRange.getColumn() + width - 1,
                            });
                        },
                    });
                    injectDisabled(setMenu, setDisabled$.current);

                    const editMenu = univerAPI.createMenu({
                        id: 'loop-block-edit',
                        title: '编辑',
                        tooltip: '编辑当前循环块配置',
                        action: () => {
                            const callback = onEditLoopBlockRef.current;
                            if (!callback) return;

                            const workbook = univerAPI.getActiveWorkbook();
                            const sheet = workbook?.getActiveSheet();
                            if (!sheet) return;

                            const activeRange = sheet.getActiveRange();
                            if (!activeRange) return;

                            const sheetId = (sheet as any).getSheetId();
                            const block = findBlockAtCell(
                                loopBlocksRef.current,
                                sheetId,
                                activeRange.getRow(),
                                activeRange.getColumn(),
                            );

                            if (block) {
                                callback(block.id);
                            }
                        },
                    });
                    injectDisabled(editMenu, editDisabled$.current);

                    const removeMenu = univerAPI.createMenu({
                        id: 'loop-block-remove',
                        title: '移除',
                        tooltip: '移除当前循环块',
                        action: () => {
                            const callback = onRemoveLoopBlockRef.current;
                            if (!callback) return;

                            const workbook = univerAPI.getActiveWorkbook();
                            const sheet = workbook?.getActiveSheet();
                            if (!sheet) return;

                            const activeRange = sheet.getActiveRange();
                            if (!activeRange) return;

                            const sheetId = (sheet as any).getSheetId();
                            const block = findBlockAtCell(
                                loopBlocksRef.current,
                                sheetId,
                                activeRange.getRow(),
                                activeRange.getColumn(),
                            );

                            if (block) {
                                callback(block.id);
                            }
                        },
                    });
                    injectDisabled(removeMenu, removeDisabled$.current);

                    // 循环块一级菜单（包含设置/编辑/移除三个子项）
                    univerAPI.createSubmenu({ id: 'loop-block', title: '循环块' })
                        .addSubmenu(setMenu)
                        .addSubmenu(editMenu)
                        .addSubmenu(removeMenu)
                        .appendTo(['contextMenu.mainArea', 'contextMenu.others']);

                }}
            />
        </div>
    );
};

export default SheetPanel;
