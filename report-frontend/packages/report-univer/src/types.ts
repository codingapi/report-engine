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

// ─── 字段拖拽 ────────────────────────────────────────────────

/** 字段拖入事件信息 */
export interface FieldDropInfo {
    sheetId: string;
    row: number;
    column: number;
    /** 原始拖拽数据，格式由消费者决定 */
    data: string;
}

// ─── 边框样式 ────────────────────────────────────────────────

/** 边框线型（与 Univer BorderStyleTypes 对应） */
export enum BorderStyleType {
    NONE = 0,
    THIN = 1,
    HAIR = 2,
    DOTTED = 3,
    DASHED = 4,
    DASH_DOT = 5,
    DASH_DOT_DOT = 6,
    DOUBLE = 7,
    MEDIUM = 8,
    MEDIUM_DASHED = 9,
    MEDIUM_DASH_DOT = 10,
    MEDIUM_DASH_DOT_DOT = 11,
    SLANT_DASH_DOT = 12,
    THICK = 13,
}

/** 单条边框信息 */
export interface BorderSide {
    style: BorderStyleType;
    /** RGB 颜色值（如 "#000000"） */
    color: string;
}

/** 单元格四边边框 */
export interface CellBorderData {
    top?: BorderSide;
    right?: BorderSide;
    bottom?: BorderSide;
    left?: BorderSide;
}

// ─── 行列尺寸 ────────────────────────────────────────────────

/** 行尺寸信息 */
export interface RowDimension {
    height: number;
    hidden: boolean;
}

/** 列尺寸信息 */
export interface ColumnDimension {
    width: number;
    hidden: boolean;
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
    /** 边框样式数据 */
    borders?: CellBorderData;
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
    /** 默认行高 */
    defaultRowHeight: number;
    /** 默认列宽 */
    defaultColumnWidth: number;
    /** 总行数 */
    rowCount: number;
    /** 总列数 */
    columnCount: number;
    /** 自定义行尺寸（仅包含非默认行） */
    rows: Record<string, RowDimension>;
    /** 自定义列尺寸（仅包含非默认列） */
    columns: Record<string, ColumnDimension>;
}

/** 完整工作簿快照 */
export interface WorkbookSnapshot {
    styles: Record<string, Record<string, unknown>>;
    sheets: SnapshotSheet[];
    /** 工作表显示顺序 */
    sheetOrder: string[];
}
