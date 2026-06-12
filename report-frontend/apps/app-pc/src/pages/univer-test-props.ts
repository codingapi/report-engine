/**
 * Univer 属性绑定验证 — 领域特定类型（报表属性模板）
 *
 * 基础类型（CellProp, CellPropStore, makeCellKey 等）已迁移至 @coding-report/report-univer
 * 此文件仅保留报表领域特定的属性模板定义
 */

// ─── 预定义属性模板 ────────────────────────────────────────

export interface PropKindTemplate {
  kind: string;
  label: string;
  description: string;
  /** 创建默认 data */
  createDefault: () => Record<string, unknown>;
}

export const PROP_KINDS: PropKindTemplate[] = [
  {
    kind: 'field',
    label: '字段绑定',
    description: '绑定数据源字段',
    createDefault: () => ({ display: 'value' }),
  },
  {
    kind: 'dataConfig',
    label: '数据配置',
    description: '数据查询参数与过滤',
    createDefault: () => ({ pageSize: 10, orderBy: '', direction: 'asc' }),
  },
  {
    kind: 'display',
    label: '显示格式',
    description: '数据展示格式设置',
    createDefault: () => ({ format: 'text', prefix: '', suffix: '' }),
  },
  {
    kind: 'aggregation',
    label: '聚合计算',
    description: '数据聚合方式',
    createDefault: () => ({ method: 'count', groupBy: '' }),
  },
];

export const PROP_KIND_MAP = Object.fromEntries(
  PROP_KINDS.map((k) => [k.kind, k]),
);
