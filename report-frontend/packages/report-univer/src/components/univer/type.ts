import type {
    SelectedCellInfo,
    FieldDropInfo,
    LoopBlockConfig,
    MenuGroupDef,
    MessageConfig,
    ExcelWorkbook,
} from '@/types';
import React from "react";

export interface UniverSheetProps {
    /** 容器样式 */
    style?: React.CSSProperties;

    /** 单元格选中回调 */
    onCellSelect?: (info: SelectedCellInfo) => void;

    /** 声明式右键菜单分组 */
    contextMenuGroups?: MenuGroupDef[];

    /** 循环块数据 — 变化时自动同步半透明高亮覆盖 */
    loopBlocks?: Record<string, LoopBlockConfig>;

    /** 字段拖入回调 */
    onFieldDrop?: (info: FieldDropInfo) => void;

    /** 消息提示（非 null 时显示，消费后调用 onMessageConsumed） */
    message?: MessageConfig | null;
    /** 消息已显示后的回调 */
    onMessageConsumed?: () => void;
}

/** 通过 ref 暴露的命令式句柄 */
export interface UniverSheetHandle {
    /** 提取当前工作簿的 Excel 格式快照 */
    getSnapshot: () => ExcelWorkbook | null;
    /** 设置指定单元格的值 */
    setCellValue: (sheetId: string, row: number, column: number, value: string) => void;
}
