import { useState } from 'react';
import { Button, Empty, Form, Modal, Popconfirm, Select, Space, Table } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataModelDataset } from '@coding-report/report-api';
import type { JoinType, Relationship } from '@/types';

const JOIN_OPTIONS: Array<{ label: string; value: JoinType }> = [
  { label: 'INNER', value: 'INNER' },
  { label: 'LEFT', value: 'LEFT' },
  { label: 'RIGHT', value: 'RIGHT' },
  { label: 'FULL', value: 'FULL' },
];

const PAGE_SIZE = 10;

function rowKey(r: Relationship): string {
  return r.id ?? `${r.left.datasetId}.${r.left.field}-${r.right.datasetId}.${r.right.field}`;
}

function datasetLabel(datasets: DataModelDataset[], datasetId: string): string {
  return datasets.find((d) => d.id === datasetId)?.alias ?? datasetId;
}

function fieldLabel(ds: DataModelDataset | undefined, fieldName: string): string {
  if (!ds) return fieldName;
  return ds.fields.find((f) => f.name === fieldName)?.alias ?? fieldName;
}

export interface RelationTabProps {
  datasets: DataModelDataset[];
  relationships: Relationship[];
  onChange: (next: Relationship[]) => void;
}

/**
 * 关系 tab：antd Table 列出关系（左数据集/左字段/连接类型/右数据集/右字段），
 * 顶部「新建关系」按钮弹 Modal 编辑，行内「编辑/删除」。
 */
export default function RelationTab({
  datasets,
  relationships,
  onChange,
}: RelationTabProps) {
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

  const openAdd = () => {
    setEditing(null);
    form.setFieldsValue({
      id: `rel-${Date.now()}`,
      left: { datasetId: datasets[0]?.id ?? '', field: '' },
      right: { datasetId: datasets[1]?.id ?? datasets[0]?.id ?? '', field: '' },
      joinType: 'INNER',
    });
    setModalOpen(true);
  };

  const openEdit = (r: Relationship) => {
    setEditing(r);
    form.setFieldsValue(r);
    setModalOpen(true);
  };

  const handleOk = async () => {
    const values = (await form.validateFields()) as Relationship;
    const next = editing
      ? relationships.map((r) => (rowKey(r) === rowKey(editing) ? values : r))
      : [...relationships, values];
    onChange(next);
    setModalOpen(false);
  };

  const handleDelete = (r: Relationship) => {
    onChange(relationships.filter((x) => rowKey(x) !== rowKey(r)));
  };

  const columns: ColumnsType<Relationship> = [
    {
      title: '左数据集',
      key: 'leftDataset',
      render: (_, r) => datasetLabel(datasets, r.left.datasetId),
    },
    {
      title: '左字段',
      key: 'leftField',
      render: (_, r) =>
        fieldLabel(datasets.find((d) => d.id === r.left.datasetId), r.left.field),
    },
    { title: '连接类型', dataIndex: 'joinType', key: 'joinType', width: 100 },
    {
      title: '右数据集',
      key: 'rightDataset',
      render: (_, r) => datasetLabel(datasets, r.right.datasetId),
    },
    {
      title: '右字段',
      key: 'rightField',
      render: (_, r) =>
        fieldLabel(datasets.find((d) => d.id === r.right.datasetId), r.right.field),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, r) => (
        <Space>
          <a onClick={() => openEdit(r)}>编辑</a>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(r)}>
            <a>删除</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const canAdd = datasets.length >= 2;

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Button
          icon={<PlusOutlined />}
          type="primary"
          onClick={openAdd}
          disabled={!canAdd}
        >
          新建关系
        </Button>
      </div>
      <Table<Relationship>
        rowKey={rowKey}
        columns={columns}
        dataSource={relationships}
        size="small"
        pagination={
          relationships.length <= PAGE_SIZE
            ? false
            : { pageSize: PAGE_SIZE, size: 'small', showSizeChanger: false }
        }
        locale={{
          emptyText: (
            <Empty
              description={
                canAdd ? '暂无关系，点上方「新建关系」' : '至少需要 2 个数据集才能创建关系'
              }
            />
          ),
        }}
      />
      <Modal
        title={editing ? '编辑关系' : '新建关系'}
        open={modalOpen}
        onOk={handleOk}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
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
          <Form.Item label="连接类型" name="joinType" rules={[{ required: true }]}>
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
