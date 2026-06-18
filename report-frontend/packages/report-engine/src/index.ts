export { ReportEngine } from './report-engine';
export type { ReportEngineHandle } from './report-engine';

export { default as ReportPreview } from './components/preview/report-preview';
export type { ReportPreviewProps } from './components/preview/report-preview';

export type {
  // 枚举
  DataType,
  Expansion,
  ExpandMode,
  Aggregation,
  CompareOperator,
  ValueType,
  JoinType,
  ParamSourceType,
  // 数据域
  Dataset,
  DatasetField,
  // 表达式域
  ReportValue,
  // 算子域
  Condition,
  // 关系域
  Relationship,
  // 参数域
  ReportParam,
  // 渲染域
  CellBinding,
  LoopBlock,
  SummaryCell,
  SummaryRow,
  // 模板预设
  TemplatePreset,
  // 报表配置
  ReportConfig,
  // 公式目录
  FunctionMeta,
  ExpressionCatalog,
  // 组件 Props
  ReportEngineProps,
} from './types';

export {
  DATA_TYPE_LABELS,
  dataTypeLabel,
  DATA_TYPE_OPTIONS,
  AGG_LABELS,
  EXPANSION_LABELS,
  OPERATOR_LABELS,
  VALUE_TYPE_LABELS,
  genId,
  findDataset,
  findField,
} from './types';

