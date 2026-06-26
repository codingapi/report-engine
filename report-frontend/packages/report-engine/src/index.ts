export { ReportEngine } from './engine';
export type { ReportEngineHandle } from './engine';

export { default as ReportPreview } from './components/preview/preview';
export type { ReportPreviewHandle, ReportPreviewProps } from './components/preview/preview';

export { default as DrillModal } from './components/preview/drill-modal';
export { default as ParamInputModal } from './components/param-input-modal';

// 数据源管理组件（原 report-datasource，已并入本包）
export { default as ConnectionForm } from './components/datasource/connection-form';
export { default as ExploreTree } from './components/datasource/explore-tree';
export { default as DatasetManager } from './components/datasource/dataset-manager';
export { default as RelationEditor } from './components/datasource/relation-editor';
export { default as DataSourceTypeManager } from './components/datasource/datasource-type-manager';
export type {
  DataSourceTypeService,
  DataSourceTypeManagerProps,
} from './components/datasource/datasource-type-manager';
export { default as DataSourceManager } from './components/datasource/datasource-manager';
export type {
  DataSourceService,
  DataSourceManagerProps,
} from './components/datasource/datasource-manager';
export { useDatasource } from './hooks/use-datasource';
export { useExplore } from './hooks/use-explore';

// 数据模型列表页
export { default as DataModelListPage } from './components/data-model/data-model-list';
export type {
  DataModelService,
  DataModelListPageProps,
} from './components/data-model/data-model-list';

// 数据模型设计页
export { default as DataModelDesigner } from './components/data-model/data-model-designer';
export type {
  DataModelDTO,
  DataModelDesignerService,
  DataModelDesignerProps,
} from './components/data-model/data-model-designer';

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
  FieldRef,
  // 数据源管理域（原 report-datasource）
  DataSourceType,
  DataSourceConfig,
  TableInfo,
  ColumnInfo,
  DatasetFieldDef,
  PhysicalDataset,
  UnionDatasetDef,
  DatasetDef,
  DatasourceService,
  ConnectionFormProps,
  ExploreTreeProps,
  DatasetManagerProps,
  RelationEditorProps,
  // 参数域
  ParamDTO,
  // 渲染域
  CellBinding,
  LoopBlock,
  SummaryCell,
  SummaryRow,
  // 模板预设
  TemplatePreset,
  // 报表配置
  ReportDTO,
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
