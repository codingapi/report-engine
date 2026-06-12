import React, { useMemo, useRef, useState } from 'react';
import { UniverSheet, MessageType, findBlockAtCell } from '@coding-report/report-univer';
import type { MenuGroupDef, CellRange, MessageConfig, FieldDropInfo, UniverSheetHandle } from '@coding-report/report-univer';
import type { SelectedCellInfo, LoopBlockConfig } from '../properties/types';

export interface SheetPanelProps {
    /** 单元格选中回调 */
    onCellSelect?: (info: SelectedCellInfo) => void;
    /** 创建循环块回调 */
    onCreateLoopBlock?: (range: CellRange) => void;
    /** 编辑循环块回调 */
    onEditLoopBlock?: (id: string) => void;
    /** 移除循环块回调 */
    onRemoveLoopBlock?: (id: string) => void;
    /** 循环块数据 */
    loopBlocks?: Record<string, LoopBlockConfig>;
    /** 字段拖入回调 — 返回要写入单元格的值 */
    onFieldDrop?: (info: FieldDropInfo) => string | undefined;
}

const SheetPanel: React.FC<SheetPanelProps> = ({
    onCellSelect,
    onCreateLoopBlock,
    onEditLoopBlock,
    onRemoveLoopBlock,
    loopBlocks = {},
    onFieldDrop,
}) => {
    const [message, setMessage] = useState<MessageConfig | null>(null);
    const sheetRef = useRef<UniverSheetHandle>(null);

    // 声明式右键菜单定义
    const contextMenuGroups = useMemo<MenuGroupDef[]>(() => ([
        {
            id: 'loop-block',
            title: '循环块',
            items: [
                {
                    id: 'loop-block-set',
                    title: '设置',
                    tooltip: '将选中区域设置为循环块',
                    onClick: (range: CellRange) => {
                        if (!onCreateLoopBlock) return;
                        const width = range.endColumn - range.startColumn + 1;
                        const height = range.endRow - range.startRow + 1;
                        if (width <= 1 && height <= 1) {
                            setMessage({
                                content: '请先选择多个单元格再设置循环块',
                                type: MessageType.Warning,
                                duration: 2000,
                            });
                            return;
                        }
                        onCreateLoopBlock(range);
                    },
                },
                {
                    id: 'loop-block-edit',
                    title: '编辑',
                    tooltip: '编辑当前循环块配置',
                    onClick: (range: CellRange) => {
                        if (!onEditLoopBlock) return;
                        const block = findBlockAtCell(loopBlocks, range.sheetId, range.startRow, range.startColumn);
                        if (block) onEditLoopBlock(block.id);
                    },
                },
                {
                    id: 'loop-block-remove',
                    title: '移除',
                    tooltip: '移除当前循环块',
                    onClick: (range: CellRange) => {
                        if (!onRemoveLoopBlock) return;
                        const block = findBlockAtCell(loopBlocks, range.sheetId, range.startRow, range.startColumn);
                        if (block) onRemoveLoopBlock(block.id);
                    },
                },
            ],
        },
    ]), [onCreateLoopBlock, onEditLoopBlock, onRemoveLoopBlock, loopBlocks]);

    // 处理字段拖入
    const handleFieldDrop = (info: FieldDropInfo) => {
        if (!onFieldDrop) return;
        const value = onFieldDrop(info);
        if (value != null && sheetRef.current) {
            sheetRef.current.setCellValue(info.sheetId, info.row, info.column, value);
        }
    };

    return (
        <div style={{ height: '100%', position: 'relative' }}>
            <UniverSheet
                ref={sheetRef}
                style={{ height: '100%' }}
                onCellSelect={onCellSelect}
                contextMenuGroups={contextMenuGroups}
                loopBlocks={loopBlocks}
                onFieldDrop={handleFieldDrop}
                message={message}
                onMessageConsumed={() => setMessage(null)}
            />
        </div>
    );
};

export default SheetPanel;
