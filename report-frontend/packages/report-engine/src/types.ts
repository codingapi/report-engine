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
export type CompareOperator = 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'CONTAINS' | 'NOT_CONTAINS' | 'IN' | 'NOT_IN' | 'IS_NULL' | 'IS_NOT_NULL' | 'BETWEEN';
export type ValueType = 'Literal' | 'FieldValue' | 'ParamValue' | 'LoopFieldValue' | 'Template' | 'Aggregate' | 'FunctionCall' | 'NameRef';
export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';
export type ParamSourceType = 'External' | 'Cell' | 'Constant';

// ─── 标签映射 ──────────────────────────────────

export const DATA_TYPE_LABELS: Record<DataType, string> = {
  STRING: '字符串', NUMBER: '数字', DATE: '日期',
  DATETIME: '日期时间', BOOLEAN: '布尔值', JSON: 'JSON',
};

/** DataType → 中文标签（找不到回退原始值） */
export function dataTypeLabel(dt: DataType | string): string {
  return DATA_TYPE_LABELS[dt as DataType] ?? dt;
}

/** Select 组件 options（参数弹窗等表单复用） */
export const DATA_TYPE_OPTIONS: Array<{ value: DataType; label: string }> =
  Object.entries(DATA_TYPE_LABELS).map(([value, label]) => ({ value: value as DataType, label }));

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
  IN: '在...之中', NOT_IN: '不在...之中', IS_NULL: '为空', IS_NOT_NULL: '不为空', BETWEEN: '介于',
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
  /** 取值/引用的真实 ID（如 "datasetId.field" / 参数名）——权威值，后端按它解析 */
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
  /** 别名/中文名称（可选，缺省用 name；在表达式选择界面展示） */
  alias?: string;
  dataType: DataType;
  /** 默认值（渲染时若外部未传值则用它；无默认值则导出/预览时必须传入） */
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
  /**
   * 独立纵向带（默认 false）。true 时本列不与同源列对齐，从自己的声明行起独立向下展开（交错排版）。
   * 代价：与其它列无法再做跨列聚合/跨列汇总/主从合并。
   */
  independent?: boolean;
  /** 表达式预览（友好文本，导出时附带存储；派生自 value，不作为权威来源） */
  preview?: string;
  /** 是否开启反查（drill-down）能力（默认 false）。开启后预览态下该格可点击，查看明细数据。 */
  drillEnabled?: boolean;
  /** 反查视图（数据集 id，可 null；null 时回退到该格字段所属数据集） */
  drillView?: string | null;
  /**
   * 单元格展示文本（设计态：别名友好文本，由 valueDisplayText(value) 正向派生）。
   * transient——保存时剥离，后端不接收也不存储。两个用途：
   * ① 写进 Univer 单元格供显示；
   * ② 作为「回声判别」基准：单元格新文本 === displayText ⇒ 程序回写的回声（忽略）；
   *    不等 ⇒ 用户手敲（退化为 Literal）。以此替代时序型 isLoadingRef，且不依赖 mutation 同步性。
   */
  displayText?: string;
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
  /** 落在交叉轴的位置（0-based）——纵向汇总是列号、横向汇总是行号。 */
  crossPos: number;
  /** 值表达式：标签（Literal/Template）或聚合（Aggregate），统一为 ReportValue */
  value: ReportValue;
  /** 表达式预览（友好文本，导出时附带存储） */
  preview?: string;
  /** 是否开启反查（drill-down）能力（默认 false）。开启后预览态下该格可点击，查看明细数据。 */
  drillEnabled?: boolean;
  /** 反查视图（数据集 id，可 null；null 时回退到该格字段所属数据集） */
  drillView?: string | null;
  /** 单元格展示文本（设计态，transient）。回声判别基准，见 CellBinding.displayText。 */
  displayText?: string;
}

/** 汇总方向：纵向在带下方追加合计行，横向在带右侧追加合计列。 */
export type SummaryAxis = 'VERTICAL' | 'HORIZONTAL';

export interface SummaryRow {
  id: string;
  /**
   * 汇总方向。缺省视为 VERTICAL（向后兼容）。
   * 由右键选区形状自动判定：横向选区（同一行）→ VERTICAL（下方合计行）；纵向选区（同一列）→ HORIZONTAL（右侧合计列）。
   */
  axis?: SummaryAxis;
  /**
   * 设计态锚定的主轴位置（0-based）——纵向是行号、横向是列号。
   * 仅前端设计态使用；渲染时 framework 仍按 groupBy 动态追加，后端用此字段定位模板样式源。
   */
  mainPos: number;
  /**
   * 汇总作用的交叉区间 [crossFrom, crossTo]（0-based，含）——纵向是列、横向是行。
   * 由右键框选区域生成。后端按该区间与数据带交叉坐标集合求交决定归属，
   * 使同一主轴位置上的多个并列汇总（各占不同交叉段）互不串扰。
   */
  crossFrom: number;
  crossTo: number;
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

/**
 * 渲染服务：由 app 层注入（桥接 report-api 的 previewReport/renderReport/drillReport）。
 * report-engine 不直接调 API，只做 UI 编排。
 */
export interface RenderService {
  /** 网页预览：发送配置 + 模板 → 返回填充数据的工作簿 + 反查格坐标 */
  preview: (request: import('@coding-report/report-api').RenderRequest) => Promise<import('@coding-report/report-api').PreviewResult>;
  /** 导出：发送配置 + 模板 → 返回 .xlsx Blob */
  export: (request: import('@coding-report/report-api').RenderRequest) => Promise<Blob>;
  /** 反查：渲染配置 + 目标格坐标 → 返回明细行 */
  drill: (params: import('@coding-report/report-api').DrillRequestParams) => Promise<import('@coding-report/report-api').DrillResult>;
}

/** 渲染入参：单元格绑定 + 循环块 + 汇总行 + 模板快照 + 报表参数。 */
export interface RenderConfig {
  bindings: CellBinding[];
  loops: LoopBlock[];
  summaries: SummaryRow[];
  workbook: ExcelWorkbook;
  params: ReportParam[];
}

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
  /**
   * 渲染服务：注入后启用内部「预览/导出」全流程（参数弹窗 → 预览抽屉 → 反查 → 抽屉内导出）。
   * 不注入则不显示预览/导出按钮。
   */
  renderService?: RenderService;
  /** 导入回调：接收文件，返回快照 */
  onImport?: (file: File) => Promise<ExcelWorkbook>;
  /** 保存报表回调：接收整张报表配置，返回报表 id（用于后续更新） */
  onSaveReport?: (config: ReportConfig) => Promise<string> | void;
  /** 字体加载回调 */
  onFontRequest?: () => Promise<FontItem[]>;
  /** 自定义操作按钮，渲染在默认按钮组左侧 */
  extraActions?: ReactNode;
  /** 自定义操作按钮，渲染在默认按钮组右侧（保存按钮左侧） */
  customActions?: ReactNode;
  /** 是否显示「导入模板」按钮（默认 true，需同时提供 onImport） */
  enableImport?: boolean;
  /** 是否显示「循环块」按钮（默认 true） */
  enableLoopBlock?: boolean;
  /** 是否显示「报表预览」按钮（默认 true，需同时提供 renderService） */
  enablePreview?: boolean;
  /** 是否显示「导出报表」按钮（默认 true，需同时提供 renderService） */
  enableExport?: boolean;
  /** 是否显示「保存报表」按钮（默认 true，需同时提供 onSaveReport） */
  enableSave?: boolean;
}
