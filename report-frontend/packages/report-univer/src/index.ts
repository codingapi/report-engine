// 组件
export { UniverSheet } from '@/components/univer';
export type { UniverSheetProps, UniverSheetHandle } from '@/components/univer/type';

// 工具函数
export { findBlockAtCell } from '@/core/geometry';

// 枚举
export { MessageType } from './types';

// 类型导出（不包含任何 @univerjs/* 类型）
export type {
    SelectedCellInfo,
    MergeRangeInfo,
    CellRange,
    LoopBlockConfig,
    MenuItemDef,
    MenuGroupDef,
    MessageConfig,
    SnapshotCell,
    SnapshotSheet,
    WorkbookSnapshot,
} from './types';
