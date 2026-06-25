import { Button, Form, Modal, Popconfirm, Select, Space, Table } from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';
import type { JoinType, RelationEditorProps, Relationship } from '@/types';

const JOIN_OPTIONS: Array<{ label: string; value: JoinType }> = [
  { label: 'INNER', value: 'INNER' },
  { label: 'LEFT', value: 'LEFT' },
  { label: 'RIGHT', value: 'RIGHT' },
  { label: 'FULL', value: 'FULL' },
];

/**
 * 关系列表编辑：在数据集之间定义 JOIN。
 * 字段下拉依赖 datasets 中各数据集的字段定义。
 */
export default function RelationEditor({
  datasets,
  relationships,
  onChange,
  disabled,
}: RelationEditorProps) {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Relationship | null>(null);
  const [form] = Form.useForm<Relationship>();

  const leftDatasetId = Form.useWatch(['left', 'datasetId'], form);
  const rightDatasetId = Form.useWatch(['right', 'datasetId'], form);

  const datasetOptions = datasets.map((d) => ({ label: d.alias ?? d.id, value: d.id }));
  const fieldOptionsOf = (datasetId?: string) => {
    if (!datasetId) return [];
    const ds = datasets.find((d) => d.id === datasetId);
    return (ds?.fields ?? []).map((f) => ({ label: f.alias ?? f.name, value: f.name }));
  };

  const columns: ColumnsType<Relationship> = [
    {
      title: '左侧',
      key: 'left',
      render: (_, r) => {
        const ds = datasets.find((d) => d.id === r.left.datasetId);
        return `${ds?.alias ?? r.left.datasetId}.${r.left.field}`;
      },
    },
    { title: 'JOIN', dataIndex: 'joinType', key: 'joinType', width: 90 },
    {
      title: '右侧',
      key: 'right',
      render: (_, r) => {
        const ds = datasets.find((d) => d.id === r.right.datasetId);
        return `${ds?.alias ?? r.right.datasetId}.${r.right.field}`;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, r) => (
        <Space>
          <a
            onClick={() => {
              setEditing(r);
              form.setFieldsValue(r);
              setModalOpen(true);
            }}
          >
            编辑
          </a>
          <Popconfirm
            title="确认删除？"
            onConfirm={() => onChange?.(relationships.filter((x) => x.id !== r.id))}
          >
            <a>删除</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleAdd = () => {
    setEditing(null);
    form.setFieldsValue({
      id: `rel-${Date.now()}`,
      left: { datasetId: datasets[0]?.id ?? '', field: '' },
      right: { datasetId: datasets[1]?.id ?? datasets[0]?.id ?? '', field: '' },
      joinType: 'INNER',
    });
    setModalOpen(true);
  };

  const handleOk = async () => {
    const values = (await form.validateFields()) as Relationship;
    const next = editing
      ? relationships.map((r) => (r.id === values.id ? values : r))
      : [...relationships, values];
    onChange?.(next);
    setModalOpen(false);
  };

  return (
    <>
      <Space style={{ marginBottom: 8 }}>
        <Button onClick={handleAdd} disabled={disabled || datasets.length < 2}>
          新建关系
        </Button>
      </Space>
      <Table<Relationship>
        rowKey="id"
        columns={columns}
        dataSource={relationships}
        pagination={false}
        size="small"
      />
      <Modal
        title={editing ? '编辑关系' : '新建关系'}
        open={modalOpen}
        onOk={handleOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="id" hidden>
            <input />
          </Form.Item>
          <Form.Item
            label="左侧数据集"
            name={['left', 'datasetId']}
            rules={[{ required: true }]}
          >
            <Select options={datasetOptions} />
          </Form.Item>
          <Form.Item label="左侧字段" name={['left', 'field']} rules={[{ required: true }]}>
            <Select options={fieldOptionsOf(leftDatasetId)} />
          </Form.Item>
          <Form.Item label="JOIN 类型" name="joinType" rules={[{ required: true }]}>
            <Select options={JOIN_OPTIONS} />
          </Form.Item>
          <Form.Item
            label="右侧数据集"
            name={['right', 'datasetId']}
            rules={[{ required: true }]}
          >
            <Select options={datasetOptions} />
          </Form.Item>
          <Form.Item label="右侧字段" name={['right', 'field']} rules={[{ required: true }]}>
            <Select options={fieldOptionsOf(rightDatasetId)} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
