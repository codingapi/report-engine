import { useCallback, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataModelDataset, UnionMember } from '@coding-report/report-api';
import type { DataType } from '@/types';
import { genId } from '@/types';

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

/** 把编辑行+左右 datasetId 组装回 DataModelDataset（kind=UNION） */
function buildUnion(
  id: string,
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
  const mapping = (rows: UnionFieldRow[]) =>
    rows.reduce<Record<string, string>>((acc, r) => {
      if (r.name.trim() && r.leftField) acc[r.name.trim()] = r.leftField;
      return acc;
    }, {});
  const members: UnionMember[] = [
    { datasetId: leftDatasetId, mapping: mapping(rows) },
    { datasetId: rightDatasetId, mapping: mapping(rows) },
  ];
  return {
    id,
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
 * 数据合集编辑器（UNION 派生数据集）：
 * - 上：合集名称/别名 + 合集字段定义（统一字段名/别名/数据类型）
 * - 下：左右两表选择 + 字段映射（每个统一字段 → 左表字段 + 右表字段）
 *
 * 合集本身存储为 kind=UNION 的 DataModelDataset，与 TABLE 同列；
 * 后端 UnionMember.mapping 为「统一字段名 → 成员实际字段名」。
 */
export default function UnionEditor({ datasets, onChange }: UnionEditorProps) {
  const { message } = AntdApp.useApp();
  const unions = useMemo(() => datasets.filter(isUnion), [datasets]);
  const tables = useMemo(() => datasets.filter(isTable), [datasets]);

  const [activeId, setActiveId] = useState<string | undefined>(
    () => unions[0]?.id,
  );
  const active = useMemo(
    () => unions.find((u) => u.id === activeId) ?? unions[0],
    [unions, activeId],
  );

  // 编辑态：直接展开 active 到 row + name/alias + 左右 id，便于即时编辑
  const [alias, setAlias] = useState<string>(active?.alias ?? '');
  const [leftId, setLeftId] = useState<string>(active?.members?.[0]?.datasetId ?? '');
  const [rightId, setRightId] = useState<string>(active?.members?.[1]?.datasetId ?? '');
  const [rows, setRows] = useState<UnionFieldRow[]>(() =>
    active ? toFieldRows(active) : [],
  );

  // active 切换时重新展开
  const switchTo = useCallback(
    (id: string | undefined) => {
      const next = id ? datasets.find((d) => d.id === id && isUnion(d)) : undefined;
      setActiveId(next?.id);
      setAlias(next?.alias ?? '');
      setLeftId(next?.members?.[0]?.datasetId ?? '');
      setRightId(next?.members?.[1]?.datasetId ?? '');
      setRows(next ? toFieldRows(next) : []);
    },
    [datasets],
  );

  const tableOptions = useMemo(
    () =>
      tables.map((t) => ({
        label: t.alias || t.sourceTable || t.id,
        value: t.id,
      })),
    [tables],
  );

  const leftTable = useMemo(
    () => tables.find((t) => t.id === leftId),
    [tables, leftId],
  );
  const rightTable = useMemo(
    () => tables.find((t) => t.id === rightId),
    [tables, rightId],
  );

  const fieldOptions = (table: DataModelDataset | undefined) =>
    (table?.fields ?? []).map((f) => ({
      label: f.alias || f.name,
      value: f.name,
    }));

  const commit = useCallback(
    (nextRows: UnionFieldRow[], nextAlias: string, nextLeft: string, nextRight: string) => {
      if (!active) return;
      const rebuilt = buildUnion(active.id, nextAlias, nextRows, nextLeft, nextRight);
      onChange(datasets.map((d) => (d.id === active.id ? rebuilt : d)));
    },
    [active, datasets, onChange],
  );

  const handleAddUnion = () => {
    if (tables.length < 2) {
      message.warning('至少需要 2 个 TABLE 数据集才能创建合集');
      return;
    }
    const id = genId();
    const initialRows = [blankRow()];
    const created = buildUnion(id, '新合集', initialRows, tables[0].id, tables[1].id);
    onChange([...datasets, created]);
    switchTo(id);
  };

  const handleRemoveUnion = (id: string) => {
    const remaining = datasets.filter((d) => d.id !== id);
    onChange(remaining);
    if (activeId === id) {
      const nextUnion = remaining.find(isUnion);
      switchTo(nextUnion?.id);
    }
  };

  const updateRow = (key: string, patch: Partial<UnionFieldRow>) => {
    const next = rows.map((r) => (r.key === key ? { ...r, ...patch } : r));
    setRows(next);
    commit(next, alias, leftId, rightId);
  };

  const addRow = () => {
    const next = [...rows, blankRow()];
    setRows(next);
    commit(next, alias, leftId, rightId);
  };

  const removeRow = (key: string) => {
    const next = rows.filter((r) => r.key !== key);
    setRows(next);
    commit(next, alias, leftId, rightId);
  };

  const handleAliasChange = (v: string) => {
    setAlias(v);
    commit(rows, v, leftId, rightId);
  };

  const handleLeftChange = (v: string) => {
    setLeftId(v);
    // 切换左表时清空失效的左字段映射
    const allowed = new Set((tables.find((t) => t.id === v)?.fields ?? []).map((f) => f.name));
    const next = rows.map((r) => ({
      ...r,
      leftField: allowed.has(r.leftField) ? r.leftField : '',
    }));
    setRows(next);
    commit(next, alias, v, rightId);
  };

  const handleRightChange = (v: string) => {
    setRightId(v);
    const allowed = new Set((tables.find((t) => t.id === v)?.fields ?? []).map((f) => f.name));
    const next = rows.map((r) => ({
      ...r,
      rightField: allowed.has(r.rightField) ? r.rightField : '',
    }));
    setRows(next);
    commit(next, alias, leftId, v);
  };

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
      width: 140,
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
      title: `左表字段${leftTable ? `（${leftTable.alias || leftTable.sourceTable}）` : ''}`,
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
      title: `右表字段${rightTable ? `（${rightTable.alias || rightTable.sourceTable}）` : ''}`,
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

  const unionSelectOptions = useMemo(
    () => unions.map((u) => ({ label: u.alias || u.id, value: u.id })),
    [unions],
  );

  return (
    <div>
      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between' }}>
        <Space>
          <Select
            style={{ width: 240 }}
            placeholder="选择合集"
            value={active?.id}
            options={unionSelectOptions}
            onChange={switchTo}
            disabled={unions.length === 0}
          />
          <Button icon={<PlusOutlined />} type="primary" onClick={handleAddUnion}>
            新建合集
          </Button>
        </Space>
        {active && (
          <Popconfirm
            title="确认删除该合集？"
            onConfirm={() => handleRemoveUnion(active.id)}
            okText="删除"
            cancelText="取消"
          >
            <Button danger icon={<DeleteOutlined />}>
              删除合集
            </Button>
          </Popconfirm>
        )}
      </div>

      {!active ? (
        <Empty description="暂无合集，点上方「新建合集」" />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <Title level={5}>合集定义</Title>
            <Space style={{ marginBottom: 8 }} wrap>
              <span>合集名称：</span>
              <Input
                style={{ width: 200 }}
                value={alias}
                placeholder="合集别名"
                onChange={(e) => handleAliasChange(e.target.value)}
              />
            </Space>
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
          </div>

          <div>
            <Title level={5}>成员映射</Title>
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
          </div>
        </div>
      )}
    </div>
  );
}
