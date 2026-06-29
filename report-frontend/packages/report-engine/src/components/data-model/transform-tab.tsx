import { useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Drawer,
  Empty,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { TransformItemInfo, TransformEntryInfo } from '@coding-report/report-api';

const { Title } = Typography;

export interface TransformTabProps {
  transforms: TransformItemInfo[];
  onChange: (transforms: TransformItemInfo[]) => void;
  /** 只读模式：隐藏新建/编辑/删除操作 */
  readOnly?: boolean;
}

interface EntryRow extends TransformEntryInfo {
  /** 前端临时 key，便于 Table 渲染 */
  key: string;
}

function toRows(item: TransformItemInfo): EntryRow[] {
  return (item.entries ?? []).map((e, i) => ({
    key: `${i}-${e.code}`,
    code: e.code,
    label: e.label,
    parent: e.parent,
  }));
}

function blankRow(): EntryRow {
  return {
    key: `row-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    code: '',
    label: '',
    parent: undefined,
  };
}

/**
 * 转换项（数据字典）编辑器：外层转换项列表 + 新建/编辑抽屉。
 *
 * - 转换项 = 一组 编码 → 呈现 映射，支持父级编码构成树形（如多级地区/分类）。
 * - 报表配置时由 map(字段, 转换项id) 引用，把字段编码渲染为呈现文本。
 * - 草稿模式：抽屉内编辑不即时写回，点「确定」才写入。
 */
export default function TransformTab({
  transforms,
  onChange,
  readOnly = false,
}: TransformTabProps) {
  const { message } = AntdApp.useApp();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [alias, setAlias] = useState('');
  const [rows, setRows] = useState<EntryRow[]>([]);

  const parentOptions = useMemo(
    () =>
      rows
        .filter((r) => r.code.trim() !== '')
        .map((r) => ({ label: r.label?.trim() ? `${r.label}（${r.code}）` : r.code, value: r.code })),
    [rows],
  );

  const openCreate = () => {
    setEditingId(null);
    setName('');
    setAlias('');
    setRows([]);
    setDrawerOpen(true);
  };

  const openEdit = (item: TransformItemInfo) => {
    setEditingId(item.id);
    setName(item.name ?? '');
    setAlias(item.alias ?? '');
    setRows(toRows(item));
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingId(null);
  };

  const handleConfirm = () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      message.warning('请输入转换项名称');
      return;
    }
    // 转换项名即 id（稳定标识）。新建校验唯一；编辑保持原 id 不变，避免改名断报表引用。
    if (!editingId && transforms.some((t) => t.id === trimmedName)) {
      message.warning('已存在同名转换项，请换一个名称');
      return;
    }
    const entries: TransformEntryInfo[] = rows
      .filter((r) => r.code.trim() !== '')
      .map((r) => ({
        code: r.code.trim(),
        label: r.label?.trim() ?? '',
        parent: r.parent?.trim() || undefined,
      }));
    if (entries.length === 0) {
      message.warning('请至少添加一条映射');
      return;
    }
    const id = editingId ?? trimmedName;
    const built: TransformItemInfo = {
      id,
      name: trimmedName,
      alias: alias.trim() || undefined,
      entries,
    };
    if (editingId) {
      onChange(transforms.map((t) => (t.id === editingId ? built : t)));
    } else {
      onChange([...transforms, built]);
    }
    message.success(editingId ? '转换项已更新' : '转换项已创建');
    closeDrawer();
  };

  const handleDelete = (id: string) => {
    onChange(transforms.filter((t) => t.id !== id));
  };

  const updateRow = (key: string, patch: Partial<EntryRow>) => {
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  };
  const addRow = () => setRows((rs) => [...rs, blankRow()]);
  const removeRow = (key: string) => setRows((rs) => rs.filter((r) => r.key !== key));

  const listColumns: ColumnsType<TransformItemInfo> = [
    { title: '标识名', dataIndex: 'name', key: 'name', render: (_, r) => r.name || r.id },
    { title: '别名', dataIndex: 'alias', key: 'alias', render: (_, r) => r.alias || '-' },
    { title: '映射条目数', key: 'count', render: (_, r) => r.entries?.length ?? 0 },
    ...(readOnly
      ? []
      : [
          {
            title: '操作',
            key: 'actions',
            width: 120,
            render: (_: unknown, r: TransformItemInfo) => (
              <Space>
                <a onClick={() => openEdit(r)}>编辑</a>
                <Popconfirm
                  title="确认删除该转换项？"
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

  const entryColumns: ColumnsType<EntryRow> = [
    {
      title: '编码',
      dataIndex: 'code',
      render: (_v, r) => (
        <Input
          size="small"
          value={r.code}
          placeholder="如 0"
          onChange={(e) => updateRow(r.key, { code: e.target.value })}
        />
      ),
    },
    {
      title: '呈现',
      dataIndex: 'label',
      render: (_v, r) => (
        <Input
          size="small"
          value={r.label}
          placeholder="如 女"
          onChange={(e) => updateRow(r.key, { label: e.target.value })}
        />
      ),
    },
    {
      title: '父级编码（可选）',
      dataIndex: 'parent',
      width: 200,
      render: (_v, r) => (
        <Select
          size="small"
          style={{ width: '100%' }}
          placeholder="顶层留空"
          value={r.parent || undefined}
          options={parentOptions.filter((o) => o.value !== r.code)}
          onChange={(v) => updateRow(r.key, { parent: v })}
          allowClear
          showSearch
          optionFilterProp="label"
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 60,
      render: (_v, r) => (
        <Popconfirm
          title="删除该映射？"
          onConfirm={() => removeRow(r.key)}
          okText="删除"
          cancelText="取消"
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      {!readOnly && (
        <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Button icon={<PlusOutlined />} type="primary" onClick={openCreate}>
            新建转换项
          </Button>
        </div>
      )}
      <Table<TransformItemInfo>
        rowKey="id"
        size="small"
        columns={listColumns}
        dataSource={transforms}
        pagination={{
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: (t) => `共 ${t} 个转换项`,
        }}
        locale={{ emptyText: <Empty description="暂无转换项，点上方「新建转换项」" /> }}
      />

      <Drawer
        title={editingId ? '编辑转换项' : '新建转换项'}
        open={drawerOpen}
        onClose={closeDrawer}
        width="50%"
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
          <Form.Item label="标识名" tooltip="报表中以 map(字段, 标识名) 引用，需唯一；建议英文">
            <Input
              value={name}
              placeholder="如 gender"
              onChange={(e) => setName(e.target.value)}
            />
          </Form.Item>
          <Form.Item label="别名" tooltip="中文名，面向用户展示；缺省用标识名">
            <Input
              value={alias}
              placeholder="如 性别"
              onChange={(e) => setAlias(e.target.value)}
            />
          </Form.Item>
        </Form>

        <Title level={5}>映射条目（编码 → 呈现）</Title>
        <Table<EntryRow>
          rowKey="key"
          size="small"
          columns={entryColumns}
          dataSource={rows}
          pagination={false}
          locale={{ emptyText: <Empty description="点下方「添加映射」" /> }}
        />
        <Button
          size="small"
          type="dashed"
          icon={<PlusOutlined />}
          onClick={addRow}
          style={{ marginTop: 8, width: '100%' }}
        >
          添加映射
        </Button>
      </Drawer>
    </div>
  );
}
