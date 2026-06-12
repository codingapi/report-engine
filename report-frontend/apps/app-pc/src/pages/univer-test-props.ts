/**
 * Univer 属性绑定验证 — 类型定义与辅助函数
 */

// ─── 属性类型 ──────────────────────────────────────────────

/**
 * 单元格属性（灵活结构，kind 区分类别，data 因类型而异）
 */
export interface CellProp {
  /** 属性类别标识 */
  kind: string;
  /** 可选的字段引用 "tableName.fieldName" */
  field?: string;
  /** 灵活数据（结构因 kind 而异） */
  data: Record<string, unknown>;
}

// ─── 存储结构 ──────────────────────────────────────────────

export interface LoopBlock {
  id: string;
  sheetId: string;
  startRow: number;
  startColumn: number;
  endRow: number;
  endColumn: number;
  label: string;
  loopVariable: string;
}

export interface CellPropStore {
  /** 单元格属性: key = `${sheetId}:${row}:${col}` */
  cellProps: Record<string, CellProp[]>;
  /** 合并区域属性: key = `merge:${sheetId}:${sr}:${sc}:${er}:${ec}` */
  mergeProps: Record<string, CellProp[]>;
  /** 循环块属性: key = blockId */
  loopBlockProps: Record<string, CellProp[]>;
}

export const EMPTY_STORE: CellPropStore = {
  cellProps: {},
  mergeProps: {},
  loopBlockProps: {},
};

// ─── Key 生成 ──────────────────────────────────────────────

export const makeCellKey = (sheetId: string, row: number, col: number): string =>
  `${sheetId}:${row}:${col}`;

export const makeMergeKey = (
  sheetId: string, sr: number, sc: number, er: number, ec: number,
): string => `merge:${sheetId}:${sr}:${sc}:${er}:${ec}`;

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
