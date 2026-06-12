import React from 'react';
import type {
    SelectedCellInfo,
    CellHandle,
    CellProp,
    FieldDropInfo,
    LoopBlockConfig,
    MenuGroupDef,
    MessageConfig,
    ExcelWorkbook,
    RenderResult,
} from '@/types';

export interface UniverSheetProps<TCellProp = CellProp, TLoopProp = CellProp> {
    /** 容器样式 */
    style?: React.CSSProperties;

    /** Univer 初始化完成后的回调（可安全调用 ref 方法） */
    onReady?: () => void;

    // ─── 单元格 ───

    /** 单元格选中回调（含操作句柄和属性） */
    onCellSelect?: (
        info: SelectedCellInfo,
        handle: CellHandle,
        cellProps: TCellProp[] | undefined,
    ) => void;

    // ─── 右键菜单 ───

    /** 声明式右键菜单分组 */
    contextMenuGroups?: MenuGroupDef[];

    // ─── 循环块 ───

    /** 循环块数据 — 变化时自动同步半透明高亮覆盖 */
    loopBlocks?: Record<string, LoopBlockConfig>;

    // ─── 拖拽 ───

    /** 字段拖入回调（含操作句柄） */
    onFieldDrop?: (info: FieldDropInfo, handle: CellHandle) => void;

    // ─── 消息 ───

    // ─── 只读模式 ───

    /** 只读模式 — 禁止单元格编辑、拖入和右键菜单操作 */
    readOnly?: boolean;

    // ─── 消息 ───

    /** 消息提示（非 null 时显示，消费后调用 onMessageConsumed） */
    message?: MessageConfig | null;
    /** 消息已显示后的回调 */
    onMessageConsumed?: () => void;

    // ─── 属性存储（用于 getSnapshot 时嵌入） ───

    /** 单元格属性: key = `${sheetId}:${row}:${col}` */
    cellProps?: Record<string, TCellProp[]>;
    /** 合并区域属性: key = `merge:${sheetId}:${sr}:${sc}:${er}:${ec}` */
    mergeProps?: Record<string, TCellProp[]>;
    /** 循环块属性: key = blockId */
    loopBlockProps?: Record<string, TLoopProp[]>;
}

/** Univer 字体配置（对应 IFontConfig） */
export interface FontConfig {
    value: string;
    label: string;
    category?: 'sans-serif' | 'serif' | 'monospace' | 'display' | 'handwriting';
}

/** 通过 ref 暴露的命令式句柄 */
export interface UniverSheetHandle<TCellProp = CellProp, TLoopProp = CellProp> {
    /** 提取当前工作簿的 Excel 格式快照（含属性） */
    getSnapshot: () => ExcelWorkbook<TCellProp, TLoopProp> | null;
    /** 将快照数据加载到工作表，返回还原的属性 */
    loadSnapshot: (snapshot: ExcelWorkbook<TCellProp, TLoopProp>) => RenderResult<TCellProp, TLoopProp> | null;
    /** 设置指定单元格的值 */
    setCellValue: (sheetId: string, row: number, column: number, value: string) => void;
    /** 设置工作表名称 */
    setSheetName: (sheetId: string, name: string) => void;
    /** 设置工作表行列数 */
    setSheetSize: (sheetId: string, rowCount: number, columnCount: number) => void;
    /** 动态注册字体到 Univer 字体列表 */
    addFonts: (fonts: FontConfig[]) => void;
}
