/**
 * 值 ↔ 文本 互转工具 — 对齐 framework `Templates` 的取值/插值语义。
 *
 * 两个用途：
 * 1. 属性面板修改 value 后，回写到 Univer 单元格的"显示文本"（设计态占位）
 * 2. Template 值的可逆编辑：parts 结构 ↔ `${...}` 文本字符串
 */

import type { ReportValue, Dataset, LoopBlock, ReportParam } from './types';
import { findField, findDataset } from './types';

// ─── 参数名 → 别名 ───────────────────────────

/** 参数名 → 别名（找不到回退参数名） */
function paramLabel(name: string | undefined, params: ReportParam[]): string {
  if (!name) return '';
  const p = params.find((pp) => pp.name === name);
  return p?.alias || name;
}

// ─── 字段引用 → 别名 ───────────────────────────

/** "datasetId.field" → "数据集别名.字段别名"（如 员工信息.工号） */
function fieldLabel(ref: string | undefined, datasets: Dataset[]): string {
  if (!ref) return '';
  const f = findField(datasets, ref);
  if (f) {
    const dot = ref.indexOf('.');
    const dsId = dot === -1 ? '' : ref.slice(0, dot);
    const ds = findDataset(datasets, dsId);
    const dsLabel = ds?.alias || dsId;
    const fLabel = f.alias || f.name;
    return `${dsLabel}.${fLabel}`;
  }
  const dot = ref.indexOf('.');
  return dot === -1 ? ref : ref.slice(dot + 1);
}

/** "loopId.field" → "循环块标签.字段别名"（如 员工循环.姓名） */
function loopFieldLabel(ref: string | undefined, datasets: Dataset[], loopBlocks: LoopBlock[]): string {
  if (!ref) return '';
  const dot = ref.indexOf('.');
  const loopId = dot === -1 ? ref : ref.slice(0, dot);
  const field = dot === -1 ? '' : ref.slice(dot + 1);
  const loop = loopBlocks.find((l) => l.id === loopId);
  const loopLabel = loop?.label || loopId;
  const ds = loop ? findDataset(datasets, loop.source.datasetId) : null;
  const fieldAlias = ds?.fields.find((f) => f.name === field)?.alias || field;
  return field ? `${loopLabel}.${fieldAlias}` : loopLabel;
}

// ─── 值 → 单元格显示文本（统一 ${} + 友好别名） ───

/**
 * 表达式 → `${}` 内部的友好字符串（不含 ${} 包裹）。
 * 数据模型字段显示别名；参数（NameRef/ParamValue）显示别名。
 */
function exprToDisplay(v: ReportValue, datasets: Dataset[], loopBlocks: LoopBlock[], params: ReportParam[] = []): string {
  switch (v.type) {
    case 'Literal':
      return v.payload || '';
    case 'FieldValue':
      return fieldLabel(v.payload, datasets);
    case 'LoopFieldValue':
      // 循环块名称 + 字段别名（如 员工循环.姓名）
      return loopFieldLabel(v.payload, datasets, loopBlocks);
    case 'NameRef':
    case 'ParamValue':
      return paramLabel(v.payload, params);
    case 'Aggregate':
      return `${v.aggregation || 'SUM'}(${v.operand ? exprToDisplay(v.operand, datasets, loopBlocks, params) : ''})`;
    case 'FunctionCall':
      return `${v.funcName || 'fn'}(${(v.args || []).map((a) => exprToDisplay(a, datasets, loopBlocks, params)).join(', ')})`;
    default:
      return '';
  }
}

/**
 * 值 → 单元格显示文本（设计态占位）。统一规则：
 * - 纯文本 Literal：直接显示文字，不带 ${}
 * - Template：文本段原样 + 每个洞包成 ${友好表达式}
 * - 其余取值/计算类（字段/聚合/函数/名称引用）：整体包成 ${友好表达式}
 */
export function valueDisplayText(
  value: ReportValue,
  datasets: Dataset[],
  loopBlocks: LoopBlock[] = [],
  params: ReportParam[] = [],
): string {
  if (value.type === 'Literal') return value.payload || '';
  if (value.type === 'Template') {
    if (!value.parts) return '';
    return value.parts
      .map((p) => (p.kind === 'text' ? p.text || '' : `\${${exprToDisplay(p.value!, datasets, loopBlocks, params)}}`))
      .join('');
  }
  return `\${${exprToDisplay(value, datasets, loopBlocks, params)}}`;
}

// ─── Template parts ↔ `${...}` 文本 ────────────

/** Value 节点 → 可逆的文本表示（用于 ExpressionBuilder 编辑） */
export function templateToString(value: ReportValue): string {
  // Template 类型：拼接文本和洞
  if (value.type === 'Template' && value.parts) {
    return value.parts
      .map((p) => (p.kind === 'text' ? p.text || '' : `\${${exprToSource(p.value)}}`))
      .join('');
  }
  // Literal 类型：直接返回文本
  if (value.type === 'Literal') {
    return value.payload || '';
  }
  // 其他类型（Aggregate, FieldValue 等）：包装为 ${...} 表达式
  return `\${${exprToSource(value)}}`;
}

/** 洞内表达式 → 源码字符串（用原始 payload，保证可逆 parse） */
function exprToSource(v: ReportValue | undefined): string {
  if (!v) return '';
  switch (v.type) {
    case 'NameRef':
    case 'ParamValue':
    case 'FieldValue':
    case 'LoopFieldValue':
    case 'Literal':
      return v.payload || '';
    case 'Aggregate':
      return `${v.aggregation || 'SUM'}(${exprToSource(v.operand)})`;
    case 'FunctionCall':
      return `${v.funcName || ''}(${(v.args || []).map(exprToSource).join(', ')})`;
    default:
      return '';
  }
}

const HOLE_RE = /\$\{([^}]*)\}/g;
const AGG_RE = /^(COUNT_DISTINCT|COUNT|SUM|AVG|MAX|MIN)\(([^)]*)\)$/;

/**
 * `${...}` 文本 → Value 节点（对齐 Java `Templates.parse`，含归约规则）：
 * - 无洞（纯文本）→ Literal
 * - 整串单个洞、无文本 → 直接返回洞内 Value（FieldValue / Aggregate / …）
 * - 文本 + 洞混合 → Template
 *
 * 洞内表达式：`${name}`→NameRef、`${d.field}`→FieldValue、`${loop.field}`→LoopFieldValue、`${SUM(d.field)}`→Aggregate
 */
export function parseTemplate(str: string, loopBlocks: LoopBlock[] = []): ReportValue {
  const parts: NonNullable<ReportValue['parts']> = [];
  let last = 0;
  let m: RegExpExecArray | null;
  HOLE_RE.lastIndex = 0;
  while ((m = HOLE_RE.exec(str)) !== null) {
    if (m.index > last) parts.push({ kind: 'text', text: str.slice(last, m.index) });
    parts.push({ kind: 'hole', value: parseExpr(m[1].trim(), loopBlocks) });
    last = m.index + m[0].length;
  }
  if (last < str.length) parts.push({ kind: 'text', text: str.slice(last) });

  // 归约：无洞→Literal；整串单洞→裸 Value；否则 Template
  const holes = parts.filter((p) => p.kind === 'hole');
  if (holes.length === 0) {
    return { type: 'Literal', payload: parts.map((p) => p.text || '').join('') };
  }
  if (parts.length === 1 && parts[0].kind === 'hole') {
    return parts[0].value!;
  }
  return { type: 'Template', parts };
}

/** 洞内表达式源码 → Value 节点 */
function parseExpr(expr: string, loopBlocks: LoopBlock[] = []): ReportValue {
  const agg = AGG_RE.exec(expr);
  if (agg) {
    return { type: 'Aggregate', aggregation: agg[1] as ReportValue['aggregation'], operand: parseExpr(agg[2].trim(), loopBlocks) };
  }
  if (expr.includes('.')) {
    // 检查是否是循环块字段引用
    const dotIndex = expr.indexOf('.');
    const prefix = expr.slice(0, dotIndex);
    const isLoopBlock = loopBlocks.some((lb) => lb.id === prefix);
    if (isLoopBlock) {
      return { type: 'LoopFieldValue', payload: expr };
    }
    return { type: 'FieldValue', payload: expr };
  }
  return { type: 'NameRef', payload: expr };
}
