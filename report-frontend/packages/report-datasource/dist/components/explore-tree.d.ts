import type { ExploreTreeProps } from '../types';
/**
 * 表/列探查树。
 * 选中表 → 触发 onSelectTable；展开表节点 → 异步拉取列；选中列 → onSelectColumn。
 */
export default function ExploreTree({ sourceId, service, onSelectTable, onSelectColumn, defaultExpandedTables, }: ExploreTreeProps): import("react").JSX.Element;
