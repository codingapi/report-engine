import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, SaveOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataModelBrief, DataModelSaveDTO } from '@coding-report/report-api';
import DataModelDesigner from './data-model-designer';
import type {
  DataModelDTO,
  DataModelDesignerHandle,
  DataModelDesignerService,
} from './data-model-designer';
import { STATUS_LABELS, statusText } from './status';

const { Title } = Typography;

const PAGE_SIZE = 10;

const formatTime = (t?: number) => (t ? new Date(t).toLocaleString() : '-');

const renderStatus = (status?: string) => {
  if (!status) return <Tag>未知</Tag>;
  const cfg = STATUS_LABELS[status];
  if (cfg) return <Tag color={cfg.color}>{cfg.text}</Tag>;
  return <Tag>{status}</Tag>;
};

/**
 * 数据模型列表页对外依赖的服务注入（app-pc 用 report-api 实现）。
 * 组件本身不直接调 API，保持纯 UI 可复用。
 */
export interface DataModelService {
  /** 分页列表 */
  list: (current: number, pageSize: number) => Promise<{
    list: DataModelBrief[];
    total: number;
  }>;
  /** 新建/更新（含 id 更新，不含新建），返回 id */
  save: (dto: DataModelSaveDTO) => Promise<string>;
  /** 删除 */
  remove: (id: string) => Promise<void>;
  /** 发布（草稿 → 已发布），发布后可被报表选用 */
  publish: (id: string) => Promise<void>;
}

export interface DataModelListPageProps {
  service: DataModelService;
  /** 设计器依赖的服务（加载/保存模型、数据源与表探查），由消费方注入 */
  designerService: DataModelDesignerService;
  pageSize?: number;
}

interface CreateForm {
  name: string;
}

export default function DataModelListPage({
  service,
  designerService,
  pageSize = PAGE_SIZE,
}: DataModelListPageProps) {
  const { message } = AntdApp.useApp();
  const [list, setList] = useState<DataModelBrief[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [designId, setDesignId] = useState<string | null>(null);
  const [designerModel, setDesignerModel] = useState<DataModelDTO | null>(null);
  const [saving, setSaving] = useState(false);
  const designerRef = useRef<DataModelDesignerHandle>(null);
  const [form] = Form.useForm<CreateForm>();

  const refresh = useCallback(
    async (targetPage = page) => {
      setLoading(true);
      try {
        const res = await service.list(targetPage, pageSize);
        setList(res.list);
        setTotal(res.total);
      } catch (e) {
        message.error(`加载数据模型列表失败: ${(e as Error).message}`);
      } finally {
        setLoading(false);
      }
    },
    [page, pageSize, service, message],
  );

  useEffect(() => {
    refresh(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onPageChange = (next: number) => {
    setPage(next);
    refresh(next);
  };

  const openCreate = () => {
    form.resetFields();
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      const id = await service.save({ name: values.name });
      message.success('数据模型已创建');
      setCreateOpen(false);
      form.resetFields();
      setDesignId(id);
    } catch (e) {
      if (e instanceof Error) {
        message.error(`创建数据模型失败: ${e.message}`);
      }
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await service.remove(id);
      message.success('数据模型已删除');
      const remaining = list.length - 1;
      const targetPage = remaining === 0 && page > 1 ? page - 1 : page;
      setPage(targetPage);
      await refresh(targetPage);
    } catch (e) {
      message.error(`删除数据模型失败: ${(e as Error).message}`);
    }
  };

  const handlePublish = async (id: string) => {
    try {
      await service.publish(id);
      message.success('数据模型已发布，现可被报表选用');
      await refresh();
    } catch (e) {
      message.error(`发布数据模型失败: ${(e as Error).message}`);
    }
  };

  /** 关闭设计抽屉并刷新列表（反映保存后的名称/时间/数据集变化） */
  const closeDesign = () => {
    setDesignId(null);
    setDesignerModel(null);
    setSaving(false);
    refresh();
  };

  /** 触发设计器保存（按钮在抽屉 header） */
  const handleDesignerSave = async () => {
    setSaving(true);
    try {
      await designerRef.current?.save();
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<DataModelBrief> = useMemo(
    () => [
      { title: '名称', dataIndex: 'name', key: 'name' },
      {
        title: '状态',
        key: 'status',
        width: 120,
        render: (_, r) => renderStatus(r.status),
      },
      {
        title: '创建时间',
        key: 'createTime',
        width: 180,
        render: (_, r) => formatTime(r.createTime),
      },
      {
        title: '更新时间',
        key: 'updateTime',
        width: 180,
        render: (_, r) => formatTime(r.updateTime),
      },
      {
        title: '操作',
        key: 'actions',
        width: 220,
        render: (_, r) => (
          <Space size="middle">
            <a onClick={() => setDesignId(r.id)}>设计</a>
            {r.status !== 'PUBLISHED' && (
              <Popconfirm
                title="确认发布该数据模型？发布后可被报表选用。"
                onConfirm={() => handlePublish(r.id)}
                okText="发布"
                cancelText="取消"
              >
                <a>发布</a>
              </Popconfirm>
            )}
            <Popconfirm
              title="确认删除该数据模型？"
              onConfirm={() => handleDelete(r.id)}
              okText="删除"
              cancelText="取消"
            >
              <a style={{ color: '#ff4d4f' }}>删除</a>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [list, page],
  );

  return (
    <div style={{ padding: 24 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Title level={4} style={{ margin: 0 }}>
          数据模型管理
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建数据模型
        </Button>
      </div>

      <Table<DataModelBrief>
        rowKey="id"
        columns={columns}
        dataSource={list}
        loading={loading}
        locale={{
          emptyText: <Empty description="暂无数据模型，点右上「新建数据模型」开始" />,
        }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: false,
          showTotal: (t) => `共 ${t} 条`,
          onChange: onPageChange,
        }}
      />

      <Modal
        title="新建数据模型"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={creating}
        okText="创建"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入数据模型名称' }]}
          >
            <Input placeholder="如 销售明细模型" onPressEnter={handleCreate} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={designId !== null}
        onClose={closeDesign}
        closable={false}
        width="100vw"
        destroyOnHidden
        styles={{ body: { padding: 0, overflow: 'auto' } }}
        title={
          designerModel ? (
            <span>
              {designerModel.name}
              <span
                style={{
                  marginLeft: 12,
                  fontSize: 12,
                  color: 'rgba(0,0,0,0.45)',
                  fontWeight: 'normal',
                }}
              >
                {statusText(designerModel.status) ? `（${statusText(designerModel.status)}）` : ''}{' '}
                最后更新 {formatTime(designerModel.updateTime)}
              </span>
            </span>
          ) : (
            '数据模型设计'
          )
        }
        extra={
          <Space>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving}
              onClick={handleDesignerSave}
            >
              保存
            </Button>
            <Button onClick={closeDesign}>关闭</Button>
          </Space>
        }
      >
        {designId && (
          <DataModelDesigner
            ref={designerRef}
            dataModelId={designId}
            service={designerService}
            onModelChange={(m) => setDesignerModel(m)}
          />
        )}
      </Drawer>
    </div>
  );
}
