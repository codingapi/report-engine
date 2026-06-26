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
}

/**
 * 数据集维护编辑器：每个数据集一个 tab，支持
 * - 表别名编辑
 * - 字段别名编辑
 * - 删除不需要的表
 */
export default function DatasetEditor({
  tables,
  onChange,
  toolbar,
  emptyHint,
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

  const renderTable = (t: WizardTable) => (
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
            源表 <Text code>{t.name}</Text>
          </Text>
        </Space>
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
        ]}
      />
    </div>
  );

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
