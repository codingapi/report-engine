import React from 'react';
import type {
  SelectedCellInfo,
  CellHandle,
  CellProp,
  FieldDropInfo,
  LoopBlockConfig,
  CellRange,
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

  // ─── 字体 ───

  /**
   * 字体数据请求回调。
   * 框架在需要字体时调用此回调，由父组件负责从 API 获取并返回。
   * 框架内部自动处理 localStorage 缓存、@font-face 注入和 Univer 注册。
   * 返回 null 或空数组表示不加载自定义字体。
   */
  onFontRequest?: () => Promise<FontItem[] | null>;

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

  /**
   * 配置单元格 — 变化时自动同步蓝底高亮（非持久装饰层，不写入导出）。
   * 用于在设计态标识"哪些单元格存在数据/表达式配置"。
   */
  highlightCells?: CellRange[];

  // ─── 拖拽 ───

  /** 字段拖入回调（含操作句柄） */
  onFieldDrop?: (info: FieldDropInfo, handle: CellHandle) => void;

  /** 单元格值变更回调（用户编辑完成后触发） */
  onCellValueChange?: (
    changes: Array<{
      sheetId: string;
      row: number;
      col: number;
      value: string;
    }>,
  ) => void;

  /**
   * 选区清除回调 — 用户执行"清除内容"/"清除格式"/"全部清除"时触发。
   * 返回受影响区域的 cellKey 列表（格式: `${sheetId}:${row}:${col}`）。
   */
  onSelectionClear?: (cellKeys: string[]) => void;

  /**
   * 行/列结构变更回调 — 用户删除/插入整行整列时触发。
   * 父组件据此对按坐标记录的属性（cellBindings/loopBlocks/summaries）做删除 + 位移同步。
   * - `dimension`: 'row' 删/插行；'col' 删/插列
   * - `action`: 'remove' 删除；'insert' 插入
   * - `start`/`count`: 起始索引（0-based）与数量
   */
  onRowsColsChanged?: (change: RowsColsChange) => void;

  /**
   * 撤销/重做阶段变更回调 — Univer 执行 undo/redo 前置为 'undo'/'redo'，重放结束后复位 'normal'。
   * 父组件据此在 undo/redo 重放期间从快照栈恢复属性，而非按当前表格内容重新推导。
   */
  onUndoRedoStateChange?: (phase: UndoRedoPhase) => void;

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

/** 行/列结构变更描述（删除/插入整行整列） */
export interface RowsColsChange {
  sheetId: string;
  dimension: 'row' | 'col';
  action: 'remove' | 'insert';
  /** 起始索引（0-based） */
  start: number;
  /** 行/列数量 */
  count: number;
  /** 触发阶段：正常操作 / 撤销重放 / 重做重放 */
  phase: UndoRedoPhase;
}

/** 撤销/重做阶段 */
export type UndoRedoPhase = 'normal' | 'undo' | 'redo';

/** 字体文件信息（由父组件通过 onFontRequest 返回） */
export interface FontItem {
  /** 字体族名（用于 CSS font-family） */
  family: string;
  /** 字体文件名 */
  filename: string;
  /** 字体文件下载地址（用于 @font-face src） */
  url: string;
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
  loadSnapshot: (
    snapshot: ExcelWorkbook<TCellProp, TLoopProp>,
  ) => RenderResult<TCellProp, TLoopProp> | null;
  /** 设置指定单元格的值 */
  setCellValue: (sheetId: string, row: number, column: number, value: string) => void;
  /** 设置工作表名称 */
  setSheetName: (sheetId: string, name: string) => void;
  /** 设置工作表行列数 */
  setSheetSize: (sheetId: string, rowCount: number, columnCount: number) => void;
  /** 动态注册字体到 Univer 字体列表 */
  addFonts: (fonts: FontConfig[]) => void;
}
