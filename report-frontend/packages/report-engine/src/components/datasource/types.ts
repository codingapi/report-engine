/**
 * 数据类型（简化后的业务类型）
 */
export enum DataType {
  STRING = 'string',         // 字符串
  NUMBER = 'number',         // 数字
  DATE = 'date',             // 日期
  DATETIME = 'datetime',     // 日期时间
  BOOLEAN = 'boolean',       // 布尔值
  JSON = 'json',             // JSON对象
}

/**
 * 外键关联（简化版）
 */
export interface ForeignKey {
  /** 关联的表名 */
  referenceTable: string;
  /** 关联的字段名 */
  referenceField: string;
}

/**
 * 字段配置
 */
export interface FieldConfig {
  /** 字段名称 */
  name: string;
  /** 字段别名/显示名称 */
  alias?: string;
  /** 数据类型 */
  dataType: DataType;
  /** 是否为主键 */
  isPrimary?: boolean;
  /** 外键关系 */
  foreignKey?: ForeignKey;
  /** 字段描述 */
  description?: string;
}

/**
 * 表配置
 */
export interface TableConfig {
  /** 表唯一标识 */
  id: string;
  /** 表名 */
  name: string;
  /** 表别名/显示名称 */
  alias?: string;
  /** 字段列表 */
  fields: FieldConfig[];
  /** 表描述 */
  description?: string;
}

/**
 * 数据配置（根）
 */
export interface DataConfig {
  /** 数据源名称 */
  name: string;
  /** 表配置列表 */
  tables: TableConfig[];
}
