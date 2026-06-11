import {type DataConfig, DataType } from '@coding-report/report-engine';

export const mockDataConfig: DataConfig = {
  name: '报表数据源',
  tables: [
    {
      id: 'user',
      name: 'sys_user',
      alias: '用户表',
      description: '系统用户信息',
      fields: [
        {
          name: 'id',
          alias: '用户ID',
          dataType: DataType.NUMBER,
          isPrimary: true,
          description: '主键ID',
        },
        {
          name: 'username',
          alias: '用户名',
          dataType: DataType.STRING,
          description: '登录用户名',
        },
        {
          name: 'email',
          alias: '邮箱',
          dataType: DataType.STRING,
          description: '用户邮箱',
        },
        {
          name: 'department_id',
          alias: '部门ID',
          dataType: DataType.NUMBER,
          foreignKey: {
            referenceTable: 'sys_department',
            referenceField: 'id',
          },
          description: '所属部门',
        },
        {
          name: 'created_at',
          alias: '创建时间',
          dataType: DataType.DATETIME,
          description: '记录创建时间',
        },
      ],
    },
    {
      id: 'department',
      name: 'sys_department',
      alias: '部门表',
      description: '组织架构部门',
      fields: [
        {
          name: 'id',
          alias: '部门ID',
          dataType: DataType.NUMBER,
          isPrimary: true,
          description: '主键ID',
        },
        {
          name: 'name',
          alias: '部门名称',
          dataType: DataType.STRING,
          description: '部门名称',
        },
        {
          name: 'parent_id',
          alias: '上级部门',
          dataType: DataType.NUMBER,
          foreignKey: {
            referenceTable: 'sys_department',
            referenceField: 'id',
          },
          description: '上级部门ID',
        },
        {
          name: 'status',
          alias: '状态',
          dataType: DataType.BOOLEAN,
          description: '是否启用',
        },
      ],
    },
    {
      id: 'order',
      name: 'biz_order',
      alias: '订单表',
      description: '业务订单信息',
      fields: [
        {
          name: 'id',
          alias: '订单ID',
          dataType: DataType.NUMBER,
          isPrimary: true,
        },
        {
          name: 'order_no',
          alias: '订单号',
          dataType: DataType.STRING,
          description: '订单编号',
        },
        {
          name: 'user_id',
          alias: '用户',
          dataType: DataType.NUMBER,
          foreignKey: {
            referenceTable: 'sys_user',
            referenceField: 'id',
          },
          description: '下单用户',
        },
        {
          name: 'amount',
          alias: '金额',
          dataType: DataType.NUMBER,
          description: '订单金额',
        },
        {
          name: 'order_date',
          alias: '下单日期',
          dataType: DataType.DATE,
          description: '下单日期',
        },
        {
          name: 'extra',
          alias: '扩展信息',
          dataType: DataType.JSON,
          description: '扩展字段',
        },
      ],
    },
  ],
};
