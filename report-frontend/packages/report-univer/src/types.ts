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

// ─── Excel 快照数据结构 ──────────────────────────────────────
// 后端友好的 Excel 数据格式，可直接映射到 Apache POI API

/** 边框线型（可读字符串） */
export type ExcelBorderStyle =
    | 'thin' | 'hair' | 'dotted' | 'dashed'
    | 'dashDot' | 'dashDotDot' | 'double' | 'medium'
    | 'mediumDashed' | 'mediumDashDot' | 'mediumDashDotDot'
    | 'slantDashDot' | 'thick';

/** 单条边框 */
export interface ExcelBorder {
    style: ExcelBorderStyle;
    /** 颜色（#RRGGBB） */
    color: string;
}

/** 四边边框 */
export interface ExcelBorders {
    top?: ExcelBorder;
    right?: ExcelBorder;
    bottom?: ExcelBorder;
    left?: ExcelBorder;
}

/** 字体样式 */
export interface ExcelFont {
    family?: string;
    size?: number;
    bold?: boolean;
    italic?: boolean;
    underline?: boolean;
    strikethrough?: boolean;
    /** 字体颜色（#RRGGBB） */
    color?: string;
}

/** 单元格样式 */
export interface ExcelStyle {
    font?: ExcelFont;
    /** 水平对齐 */
    align?: 'left' | 'center' | 'right' | 'justify' | 'distributed';
    /** 垂直对齐 */
    valign?: 'top' | 'middle' | 'bottom';
    /** 自动换行 */
    wrap?: boolean;
    /** 文字旋转角度（0-180） */
    rotation?: number;
    /** 背景填充色（#RRGGBB） */
    fill?: string;
    /** 四边边框 */
    borders?: ExcelBorders;
    /** 数字格式（如 "0.00", "#,##0", "yyyy-MM-dd"） */
    numberFormat?: string;
    /** 内边距 */
    padding?: { top?: number; right?: number; bottom?: number; left?: number };
}

/** 富文本 */
export interface ExcelRichText {
    /** 完整纯文本 */
    text: string;
    /** 分段样式 */
    segments: Array<{
        text: string;
        style?: ExcelFont;
    }>;
}

/** 合并区域 */
export interface ExcelMerge {
    /** 起始行（0-based） */
    startRow: number;
    /** 起始列（0-based） */
    startCol: number;
    /** 行数 */
    rowSpan: number;
    /** 列数 */
    colSpan: number;
}

/** 单元格数据 */
export interface ExcelCell {
    /** 行索引（0-based） */
    row: number;
    /** 列索引（0-based） */
    col: number;
    /** A1 表示法 */
    ref: string;
    /** 单元格值 */
    value: string | number | boolean | null;
    /** 公式（不含 = 前缀） */
    formula?: string;
    /** 富文本 */
    richText?: ExcelRichText;
    /** 单元格样式 */
    style?: ExcelStyle;
}

/** 自定义行高 */
export interface ExcelRow {
    index: number;
    height: number;
    hidden: boolean;
}

/** 自定义列宽 */
export interface ExcelColumn {
    index: number;
    width: number;
    hidden: boolean;
}

/** 工作表数据 */
export interface ExcelSheet {
    id: string;
    name: string;
    rowCount: number;
    columnCount: number;
    /** 默认行高（像素） */
    defaultRowHeight: number;
    /** 默认列宽（像素） */
    defaultColumnWidth: number;
    merges: ExcelMerge[];
    cells: ExcelCell[];
    rows: ExcelRow[];
    columns: ExcelColumn[];
}

/** 完整工作簿快照 */
export interface ExcelWorkbook {
    sheets: ExcelSheet[];
}
