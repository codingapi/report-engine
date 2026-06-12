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

// ─── 单元格操作句柄 ──────────────────────────────────────────

/** 单元格当前样式快照 */
export interface CellStyleSnapshot {
    fontColor?: string;
    background?: string;
    fontSize?: number;
    bold?: boolean;
}

/**
 * 单元格操作句柄
 * 在 onCellSelect 回调中返回，可直接操作选中单元格的值和样式
 */
export interface CellHandle {
    // ─── 单元格信息 ───
    readonly sheetId: string;
    readonly row: number;
    readonly col: number;
    readonly a1Notation: string;

    // ─── 值操作 ───
    setValue: (value: string | number | boolean) => void;

    // ─── 样式操作 ───
    setFontColor: (color: string) => void;
    setBackground: (color: string) => void;
    setFontSize: (size: number) => void;
    setFontWeight: (weight: 'bold' | 'normal') => void;
    setBorder: (side: 'top' | 'right' | 'bottom' | 'left', style: ExcelBorderStyle, color: string) => void;
    clearFormat: () => void;

    // ─── 样式读取 ───
    getStyle: () => CellStyleSnapshot;
}

// ─── 属性绑定（默认类型，使用方可自定义） ──────────────────

/**
 * 单元格属性（默认结构，kind 区分类别，data 因类型而异）
 * 使用方可自定义属性类型，通过泛型参数传入
 */
export interface CellProp {
    /** 属性类别标识 */
    kind: string;
    /** 可选的字段引用 "tableName.fieldName" */
    field?: string;
    /** 灵活数据（结构因 kind 而异） */
    data: Record<string, unknown>;
}

/**
 * 三层属性存储：单元格 / 合并区域 / 循环块
 */
export interface CellPropStore<TCellProp = CellProp, TLoopProp = CellProp> {
    /** 单元格属性: key = `${sheetId}:${row}:${col}` */
    cellProps: Record<string, TCellProp[]>;
    /** 合并区域属性: key = `merge:${sheetId}:${sr}:${sc}:${er}:${ec}` */
    mergeProps: Record<string, TCellProp[]>;
    /** 循环块属性: key = blockId */
    loopBlockProps: Record<string, TLoopProp[]>;
}

/** 生成单元格属性 key */
export const makeCellKey = (sheetId: string, row: number, col: number): string =>
    `${sheetId}:${row}:${col}`;

/** 生成合并区域属性 key */
export const makeMergeKey = (
    sheetId: string, sr: number, sc: number, er: number, ec: number,
): string => `merge:${sheetId}:${sr}:${sc}:${er}:${ec}`;

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
}

// ─── 循环块动态 UI ──────────────────────────────────────────

/** 循环块组件 Props（动态注册到 UniverSheet） */
export interface LoopBlockComponentProps<TLoop = CellProp> {
    /** 循环块配置 */
    block: LoopBlockConfig;
    /** 循环块属性 */
    props: TLoop[];
    /** 属性变更回调 */
    onChange: (props: TLoop[]) => void;
    /** 删除循环块回调 */
    onDelete: () => void;
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
    /** 原始拖拽数据（text/plain），格式由消费者决定 */
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
// 导入导出使用同一数据结构，保持一致性

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
export interface ExcelMerge<TCellProp = CellProp> {
    /** 起始行（0-based） */
    startRow: number;
    /** 起始列（0-based） */
    startCol: number;
    /** 行数 */
    rowSpan: number;
    /** 列数 */
    colSpan: number;
    /** 合并区域属性 */
    props?: TCellProp[];
}

/** 单元格数据 */
export interface ExcelCell<TCellProp = CellProp> {
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
    /** 单元格属性绑定 */
    props?: TCellProp[];
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

/** 循环块（Excel 快照格式） */
export interface ExcelLoopBlock<TLoopProp = CellProp> {
    id: string;
    label?: string;
    startRow: number;
    startCol: number;
    endRow: number;
    endCol: number;
    /** 循环块属性 */
    props?: TLoopProp[];
}

/** 工作表数据 */
export interface ExcelSheet<TCellProp = CellProp, TLoopProp = CellProp> {
    id: string;
    name: string;
    rowCount: number;
    columnCount: number;
    /** 默认行高（像素） */
    defaultRowHeight: number;
    /** 默认列宽（像素） */
    defaultColumnWidth: number;
    merges: ExcelMerge<TCellProp>[];
    cells: ExcelCell<TCellProp>[];
    rows: ExcelRow[];
    columns: ExcelColumn[];
    /** 循环块列表 */
    loopBlocks?: ExcelLoopBlock<TLoopProp>[];
}

/** 完整工作簿快照（导入导出共用同一结构） */
export interface ExcelWorkbook<TCellProp = CellProp, TLoopProp = CellProp> {
    sheets: ExcelSheet<TCellProp, TLoopProp>[];
}

// ─── 渲染结果 ────────────────────────────────────────────────

/** loadSnapshot 的返回结果，包含从快照中还原的属性数据 */
export interface RenderResult<TCellProp = CellProp, TLoopProp = CellProp> {
    /** 还原的单元格属性: key = `${sheetId}:${row}:${col}` */
    cellProps: Record<string, TCellProp[]>;
    /** 还原的合并区域属性: key = `merge:${sheetId}:${sr}:${sc}:${er}:${ec}` */
    mergeProps: Record<string, TCellProp[]>;
    /** 还原的循环块属性: key = blockId */
    loopBlockProps: Record<string, TLoopProp[]>;
    /** 还原的循环块配置 */
    loopBlocks: LoopBlockConfig[];
}
