// 组件
export { UniverSheet } from '@/components/univer';
export type { UniverSheetProps, UniverSheetHandle } from '@/components/univer/type';

// 工具函数
export { findBlockAtCell } from '@/core/geometry';

// 枚举
export { MessageType, BorderStyleType } from './types';

// 类型导出
export type {
    // 单元格与区域
    SelectedCellInfo,
    MergeRangeInfo,
    CellRange,
    LoopBlockConfig,
    // 菜单
    MenuItemDef,
    MenuGroupDef,
    // 消息
    MessageConfig,
    // 拖拽
    FieldDropInfo,
    // 边框
    BorderSide,
    CellBorderData,
    // 行列尺寸
    RowDimension,
    ColumnDimension,
    // 快照
    SnapshotCell,
    SnapshotSheet,
    WorkbookSnapshot,
} from './types';
