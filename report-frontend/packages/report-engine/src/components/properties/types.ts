import { DataType } from '../datasource/types';

/**
 * 条件轴类型 — 横向(Y) vs 纵向(X)
 */
export type ConditionAxis = 'y' | 'x';

/**
 * 比较运算符
 */
export enum CompareOperator {
  EQUALS = 'equals',
  NOT_EQUALS = 'not_equals',
  GREATER_THAN = 'gt',
  LESS_THAN = 'lt',
  GREATER_EQUAL = 'gte',
  LESS_EQUAL = 'lte',
  CONTAINS = 'contains',
  NOT_CONTAINS = 'not_contains',
  IN = 'in',
  NOT_IN = 'not_in',
  IS_NULL = 'is_null',
  IS_NOT_NULL = 'is_not_null',
}

/** 运算符中文标签 */
export const OPERATOR_LABELS: Record<CompareOperator, string> = {
  [CompareOperator.EQUALS]: '等于',
  [CompareOperator.NOT_EQUALS]: '不等于',
  [CompareOperator.GREATER_THAN]: '大于',
  [CompareOperator.LESS_THAN]: '小于',
  [CompareOperator.GREATER_EQUAL]: '大于等于',
  [CompareOperator.LESS_EQUAL]: '小于等于',
  [CompareOperator.CONTAINS]: '包含',
  [CompareOperator.NOT_CONTAINS]: '不包含',
  [CompareOperator.IN]: '在...之中',
  [CompareOperator.NOT_IN]: '不在...之中',
  [CompareOperator.IS_NULL]: '为空',
  [CompareOperator.IS_NOT_NULL]: '不为空',
};

/** 无需输入值的运算符 */
export const NO_VALUE_OPERATORS = new Set([
  CompareOperator.IS_NULL,
  CompareOperator.IS_NOT_NULL,
]);

/**
 * 单条条件规则
 */
export interface ConditionRule {
  /** 唯一ID（React key / 删除标识） */
  id: string;
  /** 字段标识："tableName.fieldName" */
  field: string;
  /** 比较运算符 */
  operator: CompareOperator;
  /** 值（手动输入，后续可扩展为自定义渲染器） */
  value: string;
}

/**
 * 计算方式
 */
export enum CalcMethod {
  COUNT = 'count',
  SUM = 'sum',
  AVG = 'avg',
  MAX = 'max',
  MIN = 'min',
  COUNT_DISTINCT = 'countDistinct',
  COUNT_TRUE = 'countTrue',
  COUNT_FALSE = 'countFalse',
}

/** 计算方式中文标签 */
export const CALC_METHOD_LABELS: Record<CalcMethod, string> = {
  [CalcMethod.COUNT]: '计数 (COUNT)',
  [CalcMethod.SUM]: '求和 (SUM)',
  [CalcMethod.AVG]: '平均值 (AVG)',
  [CalcMethod.MAX]: '最大值 (MAX)',
  [CalcMethod.MIN]: '最小值 (MIN)',
  [CalcMethod.COUNT_DISTINCT]: '去重计数',
  [CalcMethod.COUNT_TRUE]: '统计为真',
  [CalcMethod.COUNT_FALSE]: '统计为假',
};

/**
 * 单元格属性配置（完整）
 */
export interface CellPropertyConfig {
  /** Y轴（横向）条件 — AND 关系 */
  yConditions: ConditionRule[];
  /** X轴（纵向）条件 — AND 关系 */
  xConditions: ConditionRule[];
  /** 计算方式 */
  calcMethod: CalcMethod | null;
}

/** 空配置默认值 */
export const EMPTY_CELL_CONFIG: CellPropertyConfig = {
  yConditions: [],
  xConditions: [],
  calcMethod: null,
};

/**
 * 单元格坐标 key — `${sheetId}:${row}:${col}`
 */
export type CellKey = string;

/**
 * 所有单元格属性映射
 */
export type CellPropertyMap = Record<CellKey, CellPropertyConfig>;

/**
 * 当前选中单元格信息
 */
export interface SelectedCellInfo {
  sheetId: string;
  row: number;
  column: number;
  /** A1 表示法（如 "B3"） */
  a1Notation: string;
  /** 单元格值 */
  value: unknown;
  /** 合并单元格信息（非 null 时为合并单元格） */
  mergeRange: {
    startRow: number;
    startColumn: number;
    endRow: number;
    endColumn: number;
    /** 合并区域 A1 表示法（如 "B2:D4"） */
    a1Notation: string;
  } | null;
}

/**
 * 循环块配置
 */
export interface LoopBlockConfig {
  id: string;
  sheetId: string;
  startRow: number;
  startColumn: number;
  endRow: number;
  endColumn: number;
  /** 循环块标签 */
  label?: string;
  /** 循环变量字段 "tableName.fieldName" */
  loopVariable: string;
}

/**
 * DataType → 可选 CalcMethod 映射
 */
export const CALC_METHODS_BY_TYPE: Record<DataType, CalcMethod[]> = {
  [DataType.NUMBER]: [
    CalcMethod.COUNT,
    CalcMethod.SUM,
    CalcMethod.AVG,
    CalcMethod.MAX,
    CalcMethod.MIN,
    CalcMethod.COUNT_DISTINCT,
  ],
  [DataType.STRING]: [
    CalcMethod.COUNT,
    CalcMethod.COUNT_DISTINCT,
  ],
  [DataType.DATE]: [
    CalcMethod.COUNT,
    CalcMethod.COUNT_DISTINCT,
    CalcMethod.MIN,
    CalcMethod.MAX,
  ],
  [DataType.DATETIME]: [
    CalcMethod.COUNT,
    CalcMethod.COUNT_DISTINCT,
    CalcMethod.MIN,
    CalcMethod.MAX,
  ],
  [DataType.BOOLEAN]: [
    CalcMethod.COUNT,
    CalcMethod.COUNT_TRUE,
    CalcMethod.COUNT_FALSE,
  ],
  [DataType.JSON]: [
    CalcMethod.COUNT,
  ],
};

/**
 * DataType → 可用运算符映射
 */
export const OPERATORS_BY_TYPE: Record<DataType, CompareOperator[]> = {
  [DataType.NUMBER]: [
    CompareOperator.EQUALS, CompareOperator.NOT_EQUALS,
    CompareOperator.GREATER_THAN, CompareOperator.LESS_THAN,
    CompareOperator.GREATER_EQUAL, CompareOperator.LESS_EQUAL,
    CompareOperator.IN, CompareOperator.NOT_IN,
    CompareOperator.IS_NULL, CompareOperator.IS_NOT_NULL,
  ],
  [DataType.STRING]: [
    CompareOperator.EQUALS, CompareOperator.NOT_EQUALS,
    CompareOperator.CONTAINS, CompareOperator.NOT_CONTAINS,
    CompareOperator.IN, CompareOperator.NOT_IN,
    CompareOperator.IS_NULL, CompareOperator.IS_NOT_NULL,
  ],
  [DataType.DATE]: [
    CompareOperator.EQUALS, CompareOperator.NOT_EQUALS,
    CompareOperator.GREATER_THAN, CompareOperator.LESS_THAN,
    CompareOperator.GREATER_EQUAL, CompareOperator.LESS_EQUAL,
    CompareOperator.IS_NULL, CompareOperator.IS_NOT_NULL,
  ],
  [DataType.DATETIME]: [
    CompareOperator.EQUALS, CompareOperator.NOT_EQUALS,
    CompareOperator.GREATER_THAN, CompareOperator.LESS_THAN,
    CompareOperator.GREATER_EQUAL, CompareOperator.LESS_EQUAL,
    CompareOperator.IS_NULL, CompareOperator.IS_NOT_NULL,
  ],
  [DataType.BOOLEAN]: [
    CompareOperator.EQUALS, CompareOperator.NOT_EQUALS,
    CompareOperator.IS_NULL, CompareOperator.IS_NOT_NULL,
  ],
  [DataType.JSON]: [
    CompareOperator.IS_NULL, CompareOperator.IS_NOT_NULL,
  ],
};

/**
 * 生成单元格属性 key
 */
export const makeCellKey = (sheetId: string, row: number, col: number): CellKey =>
  `${sheetId}:${row}:${col}`;

/**
 * 生成唯一 ID
 */
export const generateId = (): string =>
  `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
