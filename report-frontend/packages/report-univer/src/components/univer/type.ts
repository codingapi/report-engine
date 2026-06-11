import type {
    SelectedCellInfo,
    LoopBlockConfig,
    MenuGroupDef,
    MessageConfig,
    WorkbookSnapshot,
} from '../../types';

export interface UniverSheetProps {
    /** 容器样式 */
    style?: React.CSSProperties;

    /** 单元格选中回调 */
    onCellSelect?: (info: SelectedCellInfo) => void;

    /** 声明式右键菜单分组 */
    contextMenuGroups?: MenuGroupDef[];

    /** 循环块数据 — 变化时自动同步蓝色虚线边框 */
    loopBlocks?: Record<string, LoopBlockConfig>;

    /** 消息提示（非 null 时显示，消费后调用 onMessageConsumed） */
    message?: MessageConfig | null;
    /** 消息已显示后的回调 */
    onMessageConsumed?: () => void;
}

/** 通过 ref 暴露的命令式句柄 */
export interface UniverSheetHandle {
    /** 提取当前工作簿的结构化快照 */
    getSnapshot: () => WorkbookSnapshot | null;
}
