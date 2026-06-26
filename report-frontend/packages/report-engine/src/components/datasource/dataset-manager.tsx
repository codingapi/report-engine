import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table } from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';
import type { DatasetDef, DatasetManagerProps, PhysicalDataset } from '@/types';

/**
 * 数据集管理：物理表数据集 + UNION 派生数据集。
 * 列表展示 + 新建（物理/UNION）/编辑/删除。
 * 字段编辑（fields）此版暂以只读展示，后续接入 ExploreTree 联动选择。
 */
export default function DatasetManager({
  datasets,
  dataSources,
  onChange,
}: DatasetManagerProps) {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DatasetDef | null>(null);
  const [form] = Form.useForm();

  const dataSourceMap = new Map(dataSources.map((d) => [d.id, d]));

  const columns: ColumnsType<DatasetDef> = [
    {
      title: '别名',
      dataIndex: 'alias',
      key: 'alias',
      render: (_, r) => r.alias ?? r.id,
    },
    {
      title: '类型',
      key: 'kind',
      render: (_, r) => (r.kind === 'PHYSICAL' ? '物理表' : 'UNION'),
    },
    {
      title: '来源',
      key: 'source',
      render: (_, r) => {
        if (r.kind === 'PHYSICAL') {
          const ds = dataSourceMap.get(r.sourceId);
          return `${ds?.name ?? r.sourceId}.${r.table}`;
        }
        return `${r.baseDatasetIds.length} 个数据集`;
      },
    },
    {
      title: '字段数',
      key: 'fields',
      render: (_, r) => r.fields.length,
    },
    {
      title: '操作',
      key: 'actions',
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
            onConfirm={() => {
              const next = datasets.filter((d) => d.id !== r.id);
              onChange?.(next);
            }}
          >
            <a>删除</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleAdd = (kind: 'PHYSICAL' | 'UNION') => {
    const newId = `ds-${Date.now()}`;
    setEditing(null);
    if (kind === 'PHYSICAL') {
      const base: PhysicalDataset = {
        id: newId,
        alias: '',
        sourceId: dataSources[0]?.id ?? '',
        table: '',
        fields: [],
      };
      form.setFieldsValue({ ...base, kind });
    } else {
      form.setFieldsValue({
        id: newId,
        alias: '',
        baseDatasetIds: [],
        fields: [],
        kind,
      });
    }
    setModalOpen(true);
  };

  const handleOk = async () => {
    const values = (await form.validateFields()) as DatasetDef;
    const next = editing
      ? datasets.map((d) => (d.id === values.id ? values : d))
      : [...datasets, values];
    onChange?.(next);
    setModalOpen(false);
  };

  return (
    <>
      <Space style={{ marginBottom: 8 }}>
        <Button onClick={() => handleAdd('PHYSICAL')}>新建物理数据集</Button>
        <Button onClick={() => handleAdd('UNION')}>新建 UNION 数据集</Button>
      </Space>
      <Table<DatasetDef>
        rowKey="id"
        columns={columns}
        dataSource={datasets}
        pagination={false}
        size="small"
      />
      <Modal
        title={editing ? '编辑数据集' : '新建数据集'}
        open={modalOpen}
        onOk={handleOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="kind" hidden>
            <Input />
          </Form.Item>
          <Form.Item label="别名" name="alias">
            <Input />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, n) => p?.kind !== n?.kind}>
            {({ getFieldValue }) => {
              const kind = getFieldValue('kind');
              if (kind === 'PHYSICAL') {
                return (
                  <>
                    <Form.Item label="数据源" name="sourceId" rules={[{ required: true }]}>
                      <Select
                        options={dataSources.map((d) => ({ label: d.name, value: d.id }))}
                      />
                    </Form.Item>
                    <Form.Item label="表名" name="table" rules={[{ required: true }]}>
                      <Input />
                    </Form.Item>
                  </>
                );
              }
              return (
                <Form.Item
                  label="参与数据集"
                  name="baseDatasetIds"
                  rules={[{ required: true, type: 'array', min: 2 }]}
                >
                  <Select
                    mode="multiple"
                    options={datasets
                      .filter((d) => d.kind === 'PHYSICAL')
                      .map((d) => ({ label: d.alias ?? d.id, value: d.id }))}
                  />
                </Form.Item>
              );
            }}
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
