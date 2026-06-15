/**
 * 值 ↔ 文本 互转工具 — 对齐 framework `Templates` 的取值/插值语义。
 *
 * 两个用途：
 * 1. 属性面板修改 value 后，回写到 Univer 单元格的"显示文本"（设计态占位）
 * 2. Template 值的可逆编辑：parts 结构 ↔ `${...}` 文本字符串
 */

import type { ReportValue, Dataset, SummaryCell, LoopBlock } from './types';
import { findField, findDataset } from './types';

// ─── 字段引用 → 别名 ───────────────────────────

/** "datasetId.field" → 字段别名（找不到回退字段名 / 原串） */
function fieldLabel(ref: string | undefined, datasets: Dataset[]): string {
  if (!ref) return '';
  const f = findField(datasets, ref);
  if (f) return f.alias || f.name;
  const dot = ref.indexOf('.');
  return dot === -1 ? ref : ref.slice(dot + 1);
}

/** "loopId.field" → "循环块名称.字段别名"（如 员工循环.姓名） */
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
 * 数据模型字段显示别名；运行时名字（NameRef/ParamValue）显示名字本身。
 */
function exprToDisplay(v: ReportValue, datasets: Dataset[], loopBlocks: LoopBlock[]): string {
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
      return v.payload || '';
    case 'Aggregate':
      return `${v.aggregation || 'SUM'}(${v.operand ? exprToDisplay(v.operand, datasets, loopBlocks) : ''})`;
    case 'FunctionCall':
      return `${v.funcName || 'fn'}(${(v.args || []).map((a) => exprToDisplay(a, datasets, loopBlocks)).join(', ')})`;
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
export function valueDisplayText(value: ReportValue, datasets: Dataset[], loopBlocks: LoopBlock[] = []): string {
  if (value.type === 'Literal') return value.payload || '';
  if (value.type === 'Template') {
    if (!value.parts) return '';
    return value.parts
      .map((p) => (p.kind === 'text' ? p.text || '' : `\${${exprToDisplay(p.value!, datasets, loopBlocks)}}`))
      .join('');
  }
  return `\${${exprToDisplay(value, datasets, loopBlocks)}}`;
}

/** 汇总单元格 → 显示文本：文本格显原文（含 ${group}），聚合格显 ${SUM(别名)} */
export function summaryCellText(cell: SummaryCell, datasets: Dataset[]): string {
  if (cell.kind === 'label') return cell.payload || '';
  return `\${${cell.aggregation || 'SUM'}(${fieldLabel(cell.payload, datasets)})}`;
}

// ─── Template parts ↔ `${...}` 文本 ────────────

/** Template 节点 → 可逆的 `${...}` 文本（也用作单元格占位） */
export function templateToString(value: ReportValue): string {
  if (value.type !== 'Template' || !value.parts) return value.payload || '';
  return value.parts
    .map((p) => (p.kind === 'text' ? p.text || '' : `\${${exprToSource(p.value)}}`))
    .join('');
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
 * `${...}` 文本 → Template 节点（对齐 Java `Templates.parse` 核心规则）：
 * - `${name}` → NameRef（晚绑定）
 * - `${d.field}` → FieldValue（含 `.` 的限定引用）
 * - `${SUM(d.field)}` → Aggregate（聚合函数）
 */
export function parseTemplate(str: string): ReportValue {
  const parts: NonNullable<ReportValue['parts']> = [];
  let last = 0;
  let m: RegExpExecArray | null;
  HOLE_RE.lastIndex = 0;
  while ((m = HOLE_RE.exec(str)) !== null) {
    if (m.index > last) parts.push({ kind: 'text', text: str.slice(last, m.index) });
    parts.push({ kind: 'hole', value: parseExpr(m[1].trim()) });
    last = m.index + m[0].length;
  }
  if (last < str.length) parts.push({ kind: 'text', text: str.slice(last) });
  return { type: 'Template', parts };
}

/** 洞内表达式源码 → Value 节点 */
function parseExpr(expr: string): ReportValue {
  const agg = AGG_RE.exec(expr);
  if (agg) {
    return { type: 'Aggregate', aggregation: agg[1] as ReportValue['aggregation'], operand: parseExpr(agg[2].trim()) };
  }
  if (expr.includes('.')) return { type: 'FieldValue', payload: expr };
  return { type: 'NameRef', payload: expr };
}
