import { useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Empty, Input, Popconfirm, Space, Table, Tabs, Tag, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { WizardField, WizardTable } from './datasource-service';

const { Text } = Typography;

export interface DatasetEditorProps {
  tables: WizardTable[];
  onChange: (tables: WizardTable[]) => void;
  /** 工具栏：解析按钮 / 提示等（渲染在数据集 tab 上方） */
  toolbar?: ReactNode;
  /** 无数据集时的占位说明 */
  emptyHint?: ReactNode;
  /** 单表重新解析（同步该表最新结构）；不传则不显示该按钮 */
  onReparseTable?: (table: WizardTable) => void;
  /** 编辑 SQL 数据集（仅对 SQL 数据集显示）；不传则不显示该按钮 */
  onEditSql?: (table: WizardTable) => void;
}

/** 是否为 SQL 数据集：sourceTable 是一段 SQL（与 name 不同且非纯表名）。 */
function isSqlDataset(t: WizardTable): boolean {
  return !!t.sourceTable && t.sourceTable !== t.name && /^\s*select\b/i.test(t.sourceTable);
}

/**
 * 数据集维护编辑器：每个数据集一个 tab，支持
 * - 表别名编辑
 * - 字段别名编辑
 * - 删除不需要的表
 * - 单表重新解析（同步结构）/ SQL 数据集编辑
 */
export default function DatasetEditor({
  tables,
  onChange,
  toolbar,
  emptyHint,
  onReparseTable,
  onEditSql,
}: DatasetEditorProps) {
  const [active, setActive] = useState<string>();

  const updateTableAlias = (name: string, alias: string) =>
    onChange(tables.map((t) => (t.name === name ? { ...t, alias } : t)));

  const removeTable = (name: string) =>
    onChange(tables.filter((t) => t.name !== name));

  const updateFieldAlias = (tableName: string, fieldName: string, alias: string) =>
    onChange(
      tables.map((t) =>
        t.name !== tableName
          ? t
          : {
              ...t,
              columns: t.columns.map((c) => (c.name === fieldName ? { ...c, alias } : c)),
            },
      ),
    );

  /** 字段上移/下移：交换相邻位置，越界忽略。顺序随数据集保存持久化，并在数据模型添加/报表渲染时保持。 */
  const moveField = (tableName: string, index: number, dir: -1 | 1) =>
    onChange(
      tables.map((t) => {
        if (t.name !== tableName) return t;
        const target = index + dir;
        if (target < 0 || target >= t.columns.length) return t;
        const cols = [...t.columns];
        [cols[index], cols[target]] = [cols[target], cols[index]];
        return { ...t, columns: cols };
      }),
    );

  const renderTable = (t: WizardTable) => {
    const sql = isSqlDataset(t);
    return (
    <div style={{ padding: '8px 12px' }}>
      <Space style={{ marginBottom: 12, justifyContent: 'space-between', width: '100%' }}>
        <Space>
          <Text type="secondary">表别名</Text>
          <Input
            style={{ width: 280 }}
            size="small"
            value={t.alias}
            placeholder={t.name}
            onChange={(e) => updateTableAlias(t.name, e.target.value)}
          />
          <Text type="secondary">
            {sql ? 'SQL 数据集' : '源表'} <Text code>{t.name}</Text>
          </Text>
        </Space>
        <Space>
          {sql && onEditSql && (
            <Button size="small" type="link" onClick={() => onEditSql(t)}>
              编辑 SQL
            </Button>
          )}
          {!sql && onReparseTable && t.sourceTable && (
            <Button size="small" type="link" onClick={() => onReparseTable(t)}>
              重新解析
            </Button>
          )}
          <Popconfirm
            title="从该数据源移除此表？"
            onConfirm={() => removeTable(t.name)}
            okText="移除"
            cancelText="取消"
          >
            <Button size="small" danger type="text" icon={<DeleteOutlined />}>
              删除此表
            </Button>
          </Popconfirm>
        </Space>
      </Space>
      <Table<WizardField>
        size="small"
        rowKey="name"
        pagination={false}
        dataSource={t.columns}
        columns={[
          {
            title: '字段',
            dataIndex: 'name',
            key: 'name',
            render: (v: string, r) => (
              <Space>
                <Text>{v}</Text>
                {r.primaryKey && <Tag color="gold">主键</Tag>}
              </Space>
            ),
          },
          { title: '类型', dataIndex: 'dataType', key: 'dataType', width: 140 },
          {
            title: '字段别名',
            key: 'alias',
            width: 260,
            render: (_, r) => (
              <Input
                size="small"
                value={r.alias}
                placeholder={r.name}
                onChange={(e) => updateFieldAlias(t.name, r.name, e.target.value)}
              />
            ),
          },
          {
            title: '排序',
            key: 'sort',
            width: 120,
            render: (_, r) => {
              const idx = t.columns.findIndex((c) => c.name === r.name);
              return (
                <Space size="small">
                  <Button
                    size="small"
                    type="text"
                    disabled={idx <= 0}
                    onClick={() => moveField(t.name, idx, -1)}
                  >
                    上移
                  </Button>
                  <Button
                    size="small"
                    type="text"
                    disabled={idx >= t.columns.length - 1}
                    onClick={() => moveField(t.name, idx, 1)}
                  >
                    下移
                  </Button>
                </Space>
              );
            },
          },
        ]}
      />
    </div>
    );
  };

  const activeKey = tables.some((t) => t.name === active) ? active : tables[0]?.name;

  return (
    <div>
      {toolbar && <div style={{ marginBottom: 12 }}>{toolbar}</div>}
      {tables.length === 0 ? (
        <Empty description={emptyHint ?? '暂无数据集'} />
      ) : (
        <Tabs
          type="card"
          activeKey={activeKey}
          onChange={setActive}
          items={tables.map((t) => ({
            key: t.name,
            label: t.alias || t.name,
            children: renderTable(t),
          }))}
        />
      )}
    </div>
  );
}
