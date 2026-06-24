export { ReportEngine } from './engine';
export type { ReportEngineHandle } from './engine';

export { default as ReportPreview } from './components/preview/preview';
export type { ReportPreviewHandle, ReportPreviewProps } from './components/preview/preview';

export { default as DrillModal } from './components/preview/drill-modal';
export { default as ParamInputModal } from './components/param-input-modal';

export { useReportPreview } from './hooks/use-report-preview';
export type { UseReportPreviewOptions } from './hooks/use-report-preview';

// 预览/导出所需的渲染契约类型（从 report-api re-export，便于消费方单包 import）
export type {
  RenderRequest,
  RenderBindingDTO,
  RenderValueDTO,
  RenderConditionDTO,
  PreviewResult,
  DrillRequestParams,
  DrillResult,
  DrillFieldInfo,
  ReportBrief,
  DataModelBrief,
} from '@coding-report/report-api';

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
  // 渲染服务（预览/导出注入）
  RenderService,
  RenderConfig,
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
