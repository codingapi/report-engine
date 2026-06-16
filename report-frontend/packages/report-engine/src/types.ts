/**
 * 报表配置领域模型 — 对齐 report-engine-framework Java 后端。
 *
 * 枚举值使用大写字符串联合类型，与 Java enum name() 一致。
 */

import type { ReactNode } from 'react';
import type { ExcelWorkbook, FontItem } from '@coding-report/report-univer';

// ─── 枚举 ─────────────────────────────────────

export type DataType = 'STRING' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'JSON';
export type Expansion = 'VERTICAL' | 'HORIZONTAL' | 'NONE';
export type ExpandMode = 'GROUP' | 'LIST';
export type Aggregation = 'NONE' | 'COUNT' | 'COUNT_DISTINCT' | 'SUM' | 'AVG' | 'MAX' | 'MIN';
export type CompareOperator = 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'CONTAINS' | 'NOT_CONTAINS' | 'IN' | 'NOT_IN' | 'IS_NULL' | 'IS_NOT_NULL';
export type ValueType = 'Literal' | 'FieldValue' | 'ParamValue' | 'LoopFieldValue' | 'Template' | 'Aggregate' | 'FunctionCall' | 'NameRef';
export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';
export type ParamSourceType = 'External' | 'Cell' | 'Constant';

// ─── 标签映射 ──────────────────────────────────

export const AGG_LABELS: Record<Aggregation, string> = {
  NONE: '不聚合', COUNT: '计数', COUNT_DISTINCT: '去重计数',
  SUM: '求和', AVG: '平均值', MAX: '最大值', MIN: '最小值',
};

export const EXPANSION_LABELS: Record<Expansion, string> = {
  VERTICAL: '↕ 纵向', HORIZONTAL: '↔ 横向', NONE: '· 不扩展',
};

export const OPERATOR_LABELS: Record<CompareOperator, string> = {
  EQ: '等于', NE: '不等于', GT: '大于', LT: '小于',
  GE: '大于等于', LE: '小于等于', CONTAINS: '包含', NOT_CONTAINS: '不包含',
  IN: '在...之中', NOT_IN: '不在...之中', IS_NULL: '为空', IS_NOT_NULL: '不为空',
};

export const VALUE_TYPE_LABELS: Record<ValueType, string> = {
  Literal: '文本', FieldValue: '字段', ParamValue: '参数', LoopFieldValue: '循环字段',
  Template: '模板', Aggregate: '聚合', FunctionCall: '函数', NameRef: '名称引用',
};

// ─── 数据域 ────────────────────────────────────

export interface DatasetField {
  name: string;
  alias: string;
  dataType: DataType;
  primaryKey?: boolean;
}

/** 数据源类型（对齐后端 DataSourceType 枚举） */
export type DataSourceType = 'CSV' | 'JSON' | 'DB' | 'API' | 'EXCEL';

export interface Dataset {
  id: string;
  alias: string;
  sourceType?: DataSourceType;
  fields: DatasetField[];
}

// ─── 表达式域 ──────────────────────────────────

/**
 * 值表达式（对齐 Java sealed Value）。
 *
 * type 决定语义，payload/operand/args/parts 按类型使用：
 * - Literal: payload = 文本值
 * - FieldValue: payload = "datasetId.field"
 * - ParamValue: payload = 参数名
 * - LoopFieldValue: payload = "loopId.field"
 * - NameRef: payload = 名字
 * - Aggregate: aggregation + operand
 * - FunctionCall: funcName + args
 * - Template: parts
 */
export interface ReportValue {
  type: ValueType;
  payload?: string;
  aggregation?: Aggregation;
  operand?: ReportValue;
  args?: ReportValue[];
  funcName?: string;
  parts?: Array<{ kind: 'text' | 'hole'; text?: string; value?: ReportValue }>;
}

// ─── 算子域 ────────────────────────────────────

export interface Condition {
  id: string;
  left: ReportValue;
  operator: CompareOperator;
  right: ReportValue | null;
}

// ─── 关系域 ────────────────────────────────────

/** 跨数据集关系（只读展示，来自后端 DataModel） */
export interface Relationship {
  left: { datasetId: string; field: string };
  right: { datasetId: string; field: string };
  joinType: JoinType;
}

// ─── 参数域 ────────────────────────────────────

/** 报表参数（报表级，设计时定义，可在表达式中以 ${name} 引用） */
export interface ReportParam {
  id: string;
  /** 表达式中引用的名字（${name}） */
  name: string;
  /** 显示名（可选，缺省用 name） */
  label?: string;
  dataType: DataType;
  /** 默认值（渲染时若外部未传值则用它） */
  defaultValue?: string;
}

// ─── 渲染域 ────────────────────────────────────

export interface CellBinding {
  cellKey: string; // "sheetId:row:col"
  value: ReportValue;
  expansion: Expansion;
  expandMode: ExpandMode;
  mergeRepeated: boolean;
  parentCell: string | null;
  conditions: Condition[];
  /** 表达式预览（友好文本，导出时附带存储；派生自 value，不作为权威来源） */
  preview?: string;
}

export interface LoopBlock {
  id: string;
  label: string;
  sheetId: string;
  startRow: number;
  startColumn: number;
  endRow: number;
  endColumn: number;
  source: {
    datasetId: string;
    filters: Condition[];
    groupBy: string[];
    orderBy: string[];
  };
}

export interface SummaryCell {
  column: number;
  /** 值表达式：标签（Literal/Template）或聚合（Aggregate），统一为 ReportValue */
  value: ReportValue;
  /** 表达式预览（友好文本，导出时附带存储） */
  preview?: string;
}

export interface SummaryRow {
  id: string;
  /**
   * 设计态锚定行号（0-based）——汇总行在模板表格中占据的实际行。
   * 仅前端设计态使用；渲染时 framework 仍按 groupBy 动态追加，后端忽略此字段。
   */
  row: number;
  /**
   * 汇总作用的列区间 [fromColumn, toColumn]（0-based，含）。
   * 由右键框选区域生成：框选起止列即区间。后端按该区间与数据带列集合求交决定归属，
   * 使同一设计行上的多个并列汇总（各占不同列段）互不串扰。
   */
  fromColumn: number;
  toColumn: number;
  groupBy: { datasetId: string; field: string } | null;
  cells: SummaryCell[];
}

// ─── 辅助 ─────────────────────────────────────

let _idCounter = 0;
export const genId = () => `r-${Date.now().toString(36)}-${++_idCounter}`;

/** 按 datasetId 查找数据集 */
export const findDataset = (datasets: Dataset[], id: string) =>
  datasets.find((d) => d.id === id);

/** 按 "datasetId.field" 查找字段 */
export const findField = (datasets: Dataset[], ref: string): DatasetField | null => {
  const dot = ref.indexOf('.');
  if (dot === -1) return null;
  const ds = findDataset(datasets, ref.slice(0, dot));
  return ds?.fields.find((f) => f.name === ref.slice(dot + 1)) ?? null;
};

// ─── 公式目录（表达式构建器用） ────────────────

/** 函数元信息（对齐后端 ExpressionController.FunctionMeta） */
export interface FunctionMeta {
  name: string;
  label: string;
  params: string[];
  description: string;
}

/** 可用公式目录：聚合 + 函数（均使用 FunctionMeta 格式） */
export interface ExpressionCatalog {
  aggregations: FunctionMeta[];
  functions: FunctionMeta[];
}

// ─── 模板预设 ────────────────────────────────

export interface TemplatePreset {
  id: string;
  label: string;
  description: string;
  cellValues: Array<{ row: number; col: number; text: string }>;
  bindings: CellBinding[];
  summaries?: SummaryRow[];
  loopBlocks?: LoopBlock[];
}

// ─── 报表配置（持久化） ────────────────────────

/** 整张报表配置（保存/加载用） */
export interface ReportConfig {
  id?: string;
  name: string;
  dataModelId?: string;
  cellBindings: CellBinding[];
  loopBlocks: LoopBlock[];
  summaries: SummaryRow[];
  params: ReportParam[];
  /** 模板表格快照 */
  template: ExcelWorkbook;
}

// ─── 组件 Props ────────────────────────────────

export interface ReportEngineProps {
  /** 数据集列表（由父组件从 API 获取后传入） */
  datasets: Dataset[];
  /** 数据关系列表（由父组件从 API 获取后传入，只读展示） */
  relationships?: Relationship[];
  /** 数据模型 ID（保存时写入配置） */
  dataModelId?: string;
  /** 可用公式目录（聚合 + 函数，由父组件从 API 获取后传入；缺省时构建器用内置聚合） */
  functions?: ExpressionCatalog;
  /** 报表标题（支持 ReactNode，可在标题区域嵌入自定义内容） */
  title?: ReactNode;
  /** 导出回调：接收配置 + 表格快照 + 报表参数 */
  onExport?: (
    bindings: CellBinding[],
    loops: LoopBlock[],
    summaries: SummaryRow[],
    workbook: ExcelWorkbook,
    params: ReportParam[],
  ) => void | Promise<void>;
  /** 导入回调：接收文件，返回快照 */
  onImport?: (file: File) => Promise<ExcelWorkbook>;
  /** 保存报表回调：接收整张报表配置，返回报表 id（用于后续更新） */
  onSaveReport?: (config: ReportConfig) => Promise<string> | void;
  /** 字体加载回调 */
  onFontRequest?: () => Promise<FontItem[]>;
}
