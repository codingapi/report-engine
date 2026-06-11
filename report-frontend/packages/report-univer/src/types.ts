// ─── 单元格选中信息 ──────────────────────────────────────────

/** 合并单元格区域信息 */
export interface MergeRangeInfo {
    startRow: number;
    startColumn: number;
    endRow: number;
    endColumn: number;
    /** A1 表示法（如 "B2:D4"） */
    a1Notation: string;
}

/** 当前选中单元格信息 */
export interface SelectedCellInfo {
    sheetId: string;
    row: number;
    column: number;
    /** A1 表示法（如 "B3"） */
    a1Notation: string;
    /** 单元格值 */
    value: unknown;
    /** 合并单元格信息（非 null 时为合并单元格） */
    mergeRange: MergeRangeInfo | null;
}

// ─── 区域范围 ────────────────────────────────────────────────

/** 工作表上的矩形区域 */
export interface CellRange {
    sheetId: string;
    startRow: number;
    startColumn: number;
    endRow: number;
    endColumn: number;
}

// ─── 循环块配置 ──────────────────────────────────────────────

/** 循环块配置 */
export interface LoopBlockConfig {
    id: string;
    sheetId: string;
    startRow: number;
    startColumn: number;
    endRow: number;
    endColumn: number;
    /** 循环块标签 */
    label?: string;
    /** 循环变量字段 "tableName.fieldName" */
    loopVariable: string;
}

// ─── 右键菜单 ────────────────────────────────────────────────

/** 单个菜单项 */
export interface MenuItemDef {
    /** 唯一标识 */
    id: string;
    /** 显示标题 */
    title: string;
    /** 提示文本 */
    tooltip?: string;
    /** 点击回调 — 接收当前选中区域 */
    onClick: (range: CellRange) => void;
}

/** 菜单分组（一级菜单包含多个子项） */
export interface MenuGroupDef {
    /** 唯一标识 */
    id: string;
    /** 分组标题 */
    title: string;
    /** 子菜单项 */
    items: MenuItemDef[];
}

// ─── 消息提示 ────────────────────────────────────────────────

/** 消息类型 */
export enum MessageType {
    Info = 'info',
    Warning = 'warning',
    Error = 'error',
    Success = 'success',
}

/** 消息配置 */
export interface MessageConfig {
    content: string;
    type?: MessageType;
    duration?: number;
}

// ─── 工作表快照 ──────────────────────────────────────────────

/** 快照中的单元格数据 */
export interface SnapshotCell {
    /** A1 表示法 */
    cell: string;
    /** 单元格值 */
    value: unknown;
    /** 富文本数据（仅当单元格有分段样式时存在） */
    richText?: {
        text: string;
        textRuns: Array<{
            text: string;
            range: string;
            style: Record<string, unknown> | null;
        }>;
    };
    /** 公式字符串（如 "=SUM(A1:A10)"） */
    formula?: string;
    /** 解析后的样式数据 */
    style?: Record<string, unknown>;
    /** 合并区域信息（仅 master 单元格） */
    merge?: {
        mergeRange: string;
        startRow: number;
        startColumn: number;
        endRow: number;
        endColumn: number;
    };
}

/** 快照中的工作表数据 */
export interface SnapshotSheet {
    sheetId: string;
    name: string;
    cells: SnapshotCell[];
    mergeData: Array<{
        startRow: number;
        startColumn: number;
        endRow: number;
        endColumn: number;
    }>;
    rowData?: Record<string, unknown>;
    columnData?: Record<string, unknown>;
}

/** 完整工作簿快照 */
export interface WorkbookSnapshot {
    styles: Record<string, Record<string, unknown>>;
    sheets: SnapshotSheet[];
}
