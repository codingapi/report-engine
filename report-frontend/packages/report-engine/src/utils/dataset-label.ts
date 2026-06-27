/**
 * 数据集展示标签：统一「别名（名称）」格式。
 *
 * - alias 与 name 都有且不同 → `别名（名称）`
 * - alias 缺省或与 name 相同 → 只显示 name（避免「表名（表名）」冗余）
 * - 都缺省 → 回退 '-'
 *
 * 全局数据集名称展示点统一用它，避免一处一个格式。
 */
export function formatDatasetLabel(alias?: string, name?: string): string {
  const a = alias?.trim();
  const n = name?.trim();
  if (a && n && a !== n) return `${a}（${n}）`;
  return n || a || '-';
}
