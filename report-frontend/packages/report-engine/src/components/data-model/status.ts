/**
 * 数据模型状态中文映射（与后端 {@code DataModelStatus} 枚举对齐：DRAFT / PUBLISHED）。
 *
 * 列表页与设计器共用，避免多处重复定义导致展示不一致。
 */
export interface StatusLabel {
  text: string;
  color: string;
}

export const STATUS_LABELS: Record<string, StatusLabel> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
};

/**
 * 状态枚举值 → 中文文本；未知或空值返回空串（调用方据此决定是否展示括号）。
 *
 * 注意：列表 Tag 的兜底仍显示原始值（便于排查后端新增状态），故列表页直接用
 * {@link STATUS_LABELS}；本函数仅用于纯文本展示场景（如设计器标题）。
 */
export function statusText(status?: string): string {
  if (!status) return '';
  return STATUS_LABELS[status]?.text ?? '';
}
