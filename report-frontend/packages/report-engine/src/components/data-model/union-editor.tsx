import { useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataModelDataset, UnionMember } from '@coding-report/report-api';
import type { DataType } from '@/types';
import { formatDatasetLabel } from '@/utils/dataset-label';

const { Title } = Typography;

const DATA_TYPES: DataType[] = [
  'STRING',
  'NUMBER',
  'DATE',
  'DATETIME',
  'BOOLEAN',
  'JSON',
];

interface UnionFieldRow {
  /** 前端临时 key，便于 Table 渲染 */
  key: string;
  name: string;
  alias: string;
  dataType: DataType;
  /** 左成员对应字段 */
  leftField: string;
  /** 右成员对应字段 */
  rightField: string;
}

export interface UnionEditorProps {
  /** 已选数据集列表（含 TABLE 与 UNION；UNION 仅取 TABLE 作为成员来源） */
  datasets: DataModelDataset[];
  onChange: (datasets: DataModelDataset[]) => void;
  /** 只读模式：隐藏新建/编辑/删除操作 */
  readOnly?: boolean;
}

function isTable(d: DataModelDataset): boolean {
  return !d.kind || d.kind === 'TABLE';
}

function isUnion(d: DataModelDataset): boolean {
  return d.kind === 'UNION';
}

function toFieldRows(union: DataModelDataset): UnionFieldRow[] {
  const left = union.members?.[0];
  const right = union.members?.[1];
  return union.fields.map((f, idx) => ({
    key: `${idx}-${f.name}`,
    name: f.name,
    alias: f.alias ?? '',
    dataType: f.dataType,
    leftField: left?.mapping?.[f.name] ?? '',
    rightField: right?.mapping?.[f.name] ?? '',
  }));
}

/**
 * 把编辑草稿组装回 UNION 数据集。
 *
 * <p>左/右成员各自用自己的字段映射（左用 {@code leftField}、右用 {@code rightField}）——
 * 修正旧实现"左右成员都用 leftField"的 bug。
 */
function buildUnion(
  id: string,
  name: string,
  alias: string,
  rows: UnionFieldRow[],
  leftDatasetId: string,
  rightDatasetId: string,
): DataModelDataset {
  const fields = rows
    .filter((r) => r.name.trim() !== '')
    .map((r) => ({
      name: r.name.trim(),
      alias: r.alias.trim() || undefined,
      dataType: r.dataType,
      primaryKey: false,
    }));
  const mappingBy = (pick: (r: UnionFieldRow) => string) =>
    rows.reduce<Record<string, string>>((acc, r) => {
      const n = r.name.trim();
      const v = pick(r);
      if (n && v) acc[n] = v;
      return acc;
    }, {});
  const members: UnionMember[] = [
    { datasetId: leftDatasetId, mapping: mappingBy((r) => r.leftField) },
    { datasetId: rightDatasetId, mapping: mappingBy((r) => r.rightField) },
  ];
  return {
    id,
    name: name.trim() || undefined,
    alias: alias.trim() || '新合集',
    kind: 'UNION',
    fields,
    members,
  };
}

function blankRow(): UnionFieldRow {
  return {
    key: `row-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    name: '',
    alias: '',
    dataType: 'STRING',
    leftField: '',
    rightField: '',
  };
}

/**
 * 数据合集（UNION 派生数据集）编辑器：外层合集列表 Table + 新建/编辑抽屉。
 *
 * - 默认展示合集列表（kind=UNION），行内「编辑/删除」。
 * - 「新建合集」/「编辑」打开 60% 抽屉：合集名称(英文)+合集别名(中文)+字段定义+左右表字段映射。
 * - 草稿模式：抽屉内编辑不即时写回，点「确定」才 buildUnion 写入 datasets。
 */
export default function UnionEditor({ datasets, onChange, readOnly = false }: UnionEditorProps) {
  const { message } = AntdApp.useApp();
  const unions = useMemo(() => datasets.filter(isUnion), [datasets]);
  const tables = useMemo(() => datasets.filter(isTable), [datasets]);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  // 抽屉草稿（确定时才写回 datasets）
  const [name, setName] = useState('');
  const [alias, setAlias] = useState('');
  const [leftId, setLeftId] = useState('');
  const [rightId, setRightId] = useState('');
  const [rows, setRows] = useState<UnionFieldRow[]>([]);

  const tableOptions = useMemo(
    () =>
      tables.map((t) => ({
        label: formatDatasetLabel(t.alias, (t.sourceTable ?? t.name) || t.id),
        value: t.id,
      })),
    [tables],
  );
  const leftTable = useMemo(() => tables.find((t) => t.id === leftId), [tables, leftId]);
  const rightTable = useMemo(() => tables.find((t) => t.id === rightId), [tables, rightId]);
  const fieldOptions = (table: DataModelDataset | undefined) =>
    (table?.fields ?? []).map((f) => ({
      label: f.alias || f.name,
      value: f.name,
    }));

  const openCreate = () => {
    if (tables.length < 2) {
      message.warning('至少需要 2 个 TABLE 数据集才能创建合集');
      return;
    }
    setEditingId(null);
    setName('');
    setAlias('');
    setLeftId('');
    setRightId('');
    setRows([]);
    setDrawerOpen(true);
  };

  const openEdit = (union: DataModelDataset) => {
    setEditingId(union.id);
    setName(union.name ?? '');
    setAlias(union.alias ?? '');
    setLeftId(union.members?.[0]?.datasetId ?? '');
    setRightId(union.members?.[1]?.datasetId ?? '');
    setRows(toFieldRows(union));
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingId(null);
  };

  const handleConfirm = () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      message.warning('请输入合集名称（英文名）');
      return;
    }
    // 合集名即 id（稳定标识）。新建时校验唯一；编辑时保持原 id 不变，避免改名断引用。
    if (!editingId && datasets.some((d) => d.id === trimmedName)) {
      message.warning('已存在同名数据集/合集，请换一个合集名称');
      return;
    }
    const id = editingId ?? trimmedName;
    const built = buildUnion(id, name, alias, rows, leftId, rightId);
    if (editingId) {
      onChange(datasets.map((d) => (d.id === editingId ? built : d)));
    } else {
      onChange([...datasets, built]);
    }
    message.success(editingId ? '合集已更新' : '合集已创建');
    closeDrawer();
  };

  const handleDelete = (id: string) => {
    onChange(datasets.filter((d) => d.id !== id));
  };

  const updateRow = (key: string, patch: Partial<UnionFieldRow>) => {
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  };
  const addRow = () => setRows((rs) => [...rs, blankRow()]);
  const removeRow = (key: string) => setRows((rs) => rs.filter((r) => r.key !== key));

  const handleLeftChange = (v: string) => {
    setLeftId(v);
    // 切换左表时清空失效的左字段映射
    const allowed = new Set((tables.find((t) => t.id === v)?.fields ?? []).map((f) => f.name));
    setRows((rs) =>
      rs.map((r) => ({ ...r, leftField: allowed.has(r.leftField) ? r.leftField : '' })),
    );
  };
  const handleRightChange = (v: string) => {
    setRightId(v);
    const allowed = new Set((tables.find((t) => t.id === v)?.fields ?? []).map((f) => f.name));
    setRows((rs) =>
      rs.map((r) => ({ ...r, rightField: allowed.has(r.rightField) ? r.rightField : '' })),
    );
  };

  const listColumns: ColumnsType<DataModelDataset> = [
    { title: '合集名称', key: 'name', render: (_, r) => r.name || '-' },
    { title: '合集别名', key: 'alias', render: (_, r) => r.alias || '-' },
    ...(readOnly
      ? []
      : [
          {
            title: '操作',
            key: 'actions',
            width: 120,
            render: (_: unknown, r: DataModelDataset) => (
              <Space>
                <a onClick={() => openEdit(r)}>编辑</a>
                <Popconfirm
                  title="确认删除该合集？"
                  onConfirm={() => handleDelete(r.id)}
                  okText="删除"
                  cancelText="取消"
                >
                  <a style={{ color: '#ff4d4f' }}>删除</a>
                </Popconfirm>
              </Space>
            ),
          },
        ]),
  ];

  const fieldColumns: ColumnsType<UnionFieldRow> = [
    {
      title: '字段名',
      dataIndex: 'name',
      render: (_v, r) => (
        <Input
          size="small"
          value={r.name}
          placeholder="unified_name"
          onChange={(e) => updateRow(r.key, { name: e.target.value })}
        />
      ),
    },
    {
      title: '别名',
      dataIndex: 'alias',
      render: (_v, r) => (
        <Input
          size="small"
          value={r.alias}
          placeholder="可选"
          onChange={(e) => updateRow(r.key, { alias: e.target.value })}
        />
      ),
    },
    {
      title: '数据类型',
      dataIndex: 'dataType',
      width: 140,
      render: (_v, r) => (
        <Select
          size="small"
          style={{ width: '100%' }}
          value={r.dataType}
          options={DATA_TYPES.map((t) => ({ label: t, value: t }))}
          onChange={(v) => updateRow(r.key, { dataType: v })}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 60,
      render: (_v, r) => (
        <Popconfirm
          title="删除该字段？"
          onConfirm={() => removeRow(r.key)}
          okText="删除"
          cancelText="取消"
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>
      ),
    },
  ];

  const mappingColumns: ColumnsType<UnionFieldRow> = [
    {
      title: '合集字段',
      dataIndex: 'name',
      render: (_v, r) => r.name || <span style={{ color: 'rgba(0,0,0,0.35)' }}>未命名</span>,
    },
    {
      title: `左表字段${leftTable ? `（${formatDatasetLabel(leftTable.alias, leftTable.sourceTable ?? leftTable.name)}）` : ''}`,
      key: 'leftField',
      render: (_v, r) => (
        <Select
          size="small"
          style={{ width: '100%' }}
          placeholder="选择左表字段"
          value={r.leftField || undefined}
          options={fieldOptions(leftTable)}
          disabled={!leftTable}
          onChange={(v) => updateRow(r.key, { leftField: v })}
          showSearch
          optionFilterProp="label"
          allowClear
        />
      ),
    },
    {
      title: `右表字段${rightTable ? `（${formatDatasetLabel(rightTable.alias, rightTable.sourceTable ?? rightTable.name)}）` : ''}`,
      key: 'rightField',
      render: (_v, r) => (
        <Select
          size="small"
          style={{ width: '100%' }}
          placeholder="选择右表字段"
          value={r.rightField || undefined}
          options={fieldOptions(rightTable)}
          disabled={!rightTable}
          onChange={(v) => updateRow(r.key, { rightField: v })}
          showSearch
          optionFilterProp="label"
          allowClear
        />
      ),
    },
  ];

  const canCreate = tables.length >= 2;

  return (
    <div>
      {!readOnly && (
        <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Button icon={<PlusOutlined />} type="primary" onClick={openCreate} disabled={!canCreate}>
            新建合集
          </Button>
        </div>
      )}
      <Table<DataModelDataset>
        rowKey="id"
        size="small"
        columns={listColumns}
        dataSource={unions}
        pagination={{
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: (t) => `共 ${t} 个合集`,
        }}
        locale={{
          emptyText: (
            <Empty
              description={
                canCreate ? '暂无合集，点上方「新建合集」' : '至少需要 2 个 TABLE 数据集才能创建合集'
              }
            />
          ),
        }}
      />

      <Drawer
        title={editingId ? '编辑合集' : '新建合集'}
        open={drawerOpen}
        onClose={closeDrawer}
        width="60%"
        destroyOnHidden
        extra={
          <Space>
            <Button type="primary" onClick={handleConfirm}>
              确定
            </Button>
            <Button onClick={closeDrawer}>取消</Button>
          </Space>
        }
      >
        <Form layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="合集名称（英文名）">
                <Input
                  value={name}
                  placeholder="如 all_employees"
                  onChange={(e) => setName(e.target.value)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="合集别名（中文名）">
                <Input
                  value={alias}
                  placeholder="如 全部员工"
                  onChange={(e) => setAlias(e.target.value)}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <Title level={5}>合集字段定义</Title>
        <Table<UnionFieldRow>
          rowKey="key"
          size="small"
          columns={fieldColumns}
          dataSource={rows}
          pagination={false}
        />
        <Button
          size="small"
          type="dashed"
          icon={<PlusOutlined />}
          onClick={addRow}
          style={{ marginTop: 8, width: '100%' }}
        >
          添加字段
        </Button>

        <Title level={5} style={{ marginTop: 24 }}>
          成员映射
        </Title>
        <Space style={{ marginBottom: 8 }} wrap>
          <span>左表：</span>
          <Select
            style={{ width: 200 }}
            value={leftId || undefined}
            placeholder="选择左表"
            options={tableOptions}
            onChange={handleLeftChange}
            showSearch
            optionFilterProp="label"
          />
          <span>右表：</span>
          <Select
            style={{ width: 200 }}
            value={rightId || undefined}
            placeholder="选择右表"
            options={tableOptions}
            onChange={handleRightChange}
            showSearch
            optionFilterProp="label"
          />
        </Space>
        <Table<UnionFieldRow>
          rowKey="key"
          size="small"
          columns={mappingColumns}
          dataSource={rows}
          pagination={false}
          locale={{
            emptyText: <Empty description="先添加合集字段" />,
          }}
        />
      </Drawer>
    </div>
  );
}
