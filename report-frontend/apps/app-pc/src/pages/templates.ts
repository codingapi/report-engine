/**
 * 模板预设配置 — 对应 ReportScenarioTest 中的 7 个测试场景。
 *
 * 每个模板定义了：
 * - cellValues: 表格中显示的文本（标题/表头）
 * - bindings: CellBinding 配置（数据绑定）
 * - summaries: 小计/总计行配置
 * - loopBlocks: 循环块配置（仅 payslipLoop 使用）
 */

import type { ReportValue, TemplatePreset } from '@coding-report/report-engine';

// ─── 辅助函数 ──────────────────────────────────

const SHEET = 'sheet1';

function fieldValue(datasetId: string, field: string): ReportValue {
  return { type: 'FieldValue', payload: `${datasetId}.${field}` };
}

function literal(text: string): ReportValue {
  return { type: 'Literal', payload: text };
}

function aggregate(agg: string, datasetId: string, field: string): ReportValue {
  return { type: 'Aggregate', aggregation: agg as any, operand: fieldValue(datasetId, field) };
}

function cellKey(row: number, col: number) {
  return `${SHEET}:${row}:${col}`;
}

function cellValue(row: number, col: number, text: string) {
  return { row, col, text };
}

// ─── 模板定义 ──────────────────────────────────

/** 1. 简单列表：商品名 + 价格 + 合计 */
export const simpleListTemplate: TemplatePreset = {
  id: 'simpleList',
  label: '简单列表',
  description: '商品清单 + 价格合计',
  cellValues: [
    cellValue(0, 0, '商品清单'),
    cellValue(1, 0, '商品名'),
    cellValue(1, 1, '价格'),
  ],
  bindings: [
    { cellKey: cellKey(0, 0), value: literal('商品清单'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 0), value: literal('商品名'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 1), value: literal('价格'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 0), value: fieldValue('products', 'name'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 1), value: fieldValue('products', 'price'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
  ],
  summaries: [
    {
      id: 'sum-total',
      row: 3,
      groupBy: null,
      cells: [
        { column: 0, kind: 'label', payload: '合计' },
        { column: 1, kind: 'agg', payload: 'products.price', aggregation: 'SUM' },
      ],
    },
  ],
};

/** 2. 分组列表：销售分类合并 + 商品明细 */
export const mergedListTemplate: TemplatePreset = {
  id: 'mergedList',
  label: '分组列表',
  description: '销售明细（分类合并）',
  cellValues: [
    cellValue(0, 0, '销售明细'),
    cellValue(1, 0, '分类'),
    cellValue(1, 1, '商品'),
    cellValue(1, 2, '数量'),
  ],
  bindings: [
    { cellKey: cellKey(0, 0), value: literal('销售明细'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 0), value: literal('分类'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 1), value: literal('商品'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 2), value: literal('数量'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 0), value: fieldValue('sales', 'category'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 1), value: fieldValue('sales', 'product'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 2), value: fieldValue('sales', 'qty'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
  ],
};

/** 3. 多级分组统计：单位 > 部门 > 人数 */
export const statisticsTemplate: TemplatePreset = {
  id: 'statistics',
  label: '多级分组',
  description: '人员统计（单位>部门>计数）',
  cellValues: [
    cellValue(0, 0, '人员统计'),
    cellValue(1, 0, '单位'),
    cellValue(1, 1, '部门'),
    cellValue(1, 2, '人数'),
    cellValue(1, 3, '总人数'),
  ],
  bindings: [
    { cellKey: cellKey(0, 0), value: literal('人员统计'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 0), value: literal('单位'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 1), value: literal('部门'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 2), value: literal('人数'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 3), value: literal('总人数'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 0), value: fieldValue('staff', 'unit'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 1), value: fieldValue('staff', 'dept'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: cellKey(2, 0), conditions: [] },
    { cellKey: cellKey(2, 2), value: aggregate('COUNT', 'staff', 'name'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: cellKey(2, 1), conditions: [] },
    { cellKey: cellKey(2, 3), value: aggregate('COUNT', 'staff', 'name'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: true, parentCell: cellKey(2, 0), conditions: [] },
  ],
};

/** 4. 主从合并：员工 + 学历（1:N） */
export const masterDetailTemplate: TemplatePreset = {
  id: 'masterDetail',
  label: '主从合并',
  description: '员工学历（1:N 合并）',
  cellValues: [
    cellValue(0, 0, '员工学历信息表'),
    cellValue(1, 0, '姓名'),
    cellValue(1, 1, '性别'),
    cellValue(1, 2, '年龄'),
    cellValue(1, 3, '学校'),
    cellValue(1, 4, '专业'),
    cellValue(1, 5, '毕业时间'),
  ],
  bindings: [
    { cellKey: cellKey(0, 0), value: literal('员工学历信息表'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 0), value: literal('姓名'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 1), value: literal('性别'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 2), value: literal('年龄'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 3), value: literal('学校'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 4), value: literal('专业'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 5), value: literal('毕业时间'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 0), value: fieldValue('emp_basic', 'name'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 1), value: fieldValue('emp_basic', 'gender'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: cellKey(2, 0), conditions: [] },
    { cellKey: cellKey(2, 2), value: fieldValue('emp_basic', 'age'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: cellKey(2, 1), conditions: [] },
    { cellKey: cellKey(2, 3), value: fieldValue('emp_education', 'school'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 4), value: fieldValue('emp_education', 'major'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 5), value: fieldValue('emp_education', 'graduate_time'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
  ],
};

/** 5. 小计+总计：部门薪资分组小计 + 总计 */
export const subtotalTemplate: TemplatePreset = {
  id: 'subtotal',
  label: '小计+总计',
  description: '薪资统计（单位小计+总计）',
  cellValues: [
    cellValue(0, 0, '单位部门薪资统计表'),
    cellValue(1, 0, '单位'),
    cellValue(1, 1, '部门'),
    cellValue(1, 2, '姓名'),
    cellValue(1, 3, '薪资'),
  ],
  bindings: [
    { cellKey: cellKey(0, 0), value: literal('单位部门薪资统计表'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 0), value: literal('单位'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 1), value: literal('部门'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 2), value: literal('姓名'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 3), value: literal('薪资'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 0), value: fieldValue('salary_detail', 'unit'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 1), value: fieldValue('salary_detail', 'dept'), expansion: 'VERTICAL', expandMode: 'GROUP', mergeRepeated: true, parentCell: cellKey(2, 0), conditions: [] },
    { cellKey: cellKey(2, 2), value: fieldValue('salary_detail', 'name'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(2, 3), value: fieldValue('salary_detail', 'salary'), expansion: 'VERTICAL', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
  ],
  summaries: [
    {
      id: 'sum-unit',
      row: 3,
      groupBy: { datasetId: 'salary_detail', field: 'unit' },
      cells: [
        { column: 1, kind: 'label', payload: '${group}小计' },
        { column: 3, kind: 'agg', payload: 'salary_detail.salary', aggregation: 'SUM' },
      ],
    },
    {
      id: 'sum-total',
      row: 4,
      groupBy: null,
      cells: [
        { column: 0, kind: 'label', payload: '总计' },
        { column: 3, kind: 'agg', payload: 'salary_detail.salary', aggregation: 'SUM' },
      ],
    },
  ],
};

/** 6. 薪资条循环：员工循环 + 跨源查询 */
export const payslipLoopTemplate: TemplatePreset = {
  id: 'payslipLoop',
  label: '薪资条循环',
  description: '每人一张薪资条（循环块）',
  cellValues: [
    cellValue(0, 0, '${name}的薪资'),
    cellValue(1, 0, '总薪资'),
    cellValue(1, 1, '岗位薪资'),
    cellValue(1, 2, '绩效工资'),
  ],
  bindings: [
    { cellKey: cellKey(0, 0), value: { type: 'Template', parts: [{ kind: 'hole', value: { type: 'NameRef', payload: 'name' } }, { kind: 'text', text: '的薪资' }] }, expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 0), value: literal('总薪资'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 1), value: literal('岗位薪资'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    { cellKey: cellKey(1, 2), value: literal('绩效工资'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null, conditions: [] },
    {
      cellKey: cellKey(2, 0), value: fieldValue('salaries', 'total'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null,
      conditions: [{ id: 'c1', left: fieldValue('salaries', 'emp_id'), operator: 'EQ', right: { type: 'LoopFieldValue', payload: 'loop_emp.id' } }],
    },
    {
      cellKey: cellKey(2, 1), value: fieldValue('salaries', 'base'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null,
      conditions: [{ id: 'c2', left: fieldValue('salaries', 'emp_id'), operator: 'EQ', right: { type: 'LoopFieldValue', payload: 'loop_emp.id' } }],
    },
    {
      cellKey: cellKey(2, 2), value: fieldValue('salaries', 'bonus'), expansion: 'NONE', expandMode: 'LIST', mergeRepeated: false, parentCell: null,
      conditions: [{ id: 'c3', left: fieldValue('salaries', 'emp_id'), operator: 'EQ', right: { type: 'LoopFieldValue', payload: 'loop_emp.id' } }],
    },
  ],
  loopBlocks: [
    {
      id: 'loop_emp',
      label: '员工循环',
      sheetId: SHEET,
      startRow: 0, startColumn: 0,
      endRow: 3, endColumn: 2,
      source: {
        datasetId: 'employees',
        filters: [{ id: 'lf1', left: fieldValue('employees', 'status'), operator: 'EQ', right: literal('在职') }],
        groupBy: [],
        orderBy: [],
      },
    },
  ],
};

/** 所有模板预设 */
export const ALL_TEMPLATES: TemplatePreset[] = [
  simpleListTemplate,
  mergedListTemplate,
  statisticsTemplate,
  masterDetailTemplate,
  subtotalTemplate,
  payslipLoopTemplate,
];
