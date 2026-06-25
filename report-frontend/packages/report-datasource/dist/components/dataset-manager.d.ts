import type { DatasetManagerProps } from '../types';
/**
 * 数据集管理：物理表数据集 + UNION 派生数据集。
 * 列表展示 + 新建（物理/UNION）/编辑/删除。
 * 字段编辑（fields）此版暂以只读展示，后续接入 ExploreTree 联动选择。
 */
export default function DatasetManager({ datasets, dataSources, onChange, }: DatasetManagerProps): import("react").JSX.Element;
