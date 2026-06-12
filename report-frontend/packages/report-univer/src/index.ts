// 组件
export { UniverSheet } from '@/components/univer';
export type { UniverSheetProps, UniverSheetHandle } from '@/components/univer/type';
export { DefaultLoopBlockPanel } from '@/components/loop-block-panel';

// 工具函数
export { findBlockAtCell } from '@/core/geometry';
export { extractSnapshot } from '@/core/snapshot';
export type { ExtractOptions } from '@/core/snapshot';
export { renderSnapshot } from '@/core/render';
export { makeCellKey, makeMergeKey } from './types';

// 枚举
export { MessageType } from './types';

// 类型导出
export type {
    // 单元格与区域
    SelectedCellInfo,
    MergeRangeInfo,
    CellRange,
    // 属性绑定
    CellProp,
    CellPropStore,
    // 单元格操作句柄
    CellHandle,
    CellStyleSnapshot,
    // 循环块
    LoopBlockConfig,
    LoopBlockComponentProps,
    // 菜单
    MenuItemDef,
    MenuGroupDef,
    // 消息
    MessageConfig,
    // 拖拽
    FieldDropInfo,
    // Excel 快照（导入导出共用）
    ExcelWorkbook,
    ExcelSheet,
    ExcelCell,
    ExcelStyle,
    ExcelFont,
    ExcelBorders,
    ExcelBorder,
    ExcelBorderStyle,
    ExcelRichText,
    ExcelMerge,
    ExcelRow,
    ExcelColumn,
    ExcelLoopBlock,
    // 渲染结果
    RenderResult,
} from './types';
