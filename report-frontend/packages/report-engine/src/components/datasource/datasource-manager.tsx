import { useCallback, useEffect, useMemo, useState } from 'react';
import { App as AntdApp, Button, Empty, Popconfirm, Space, Table, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataSourceBrief } from '@coding-report/report-api';
import DataSourceWizard from './datasource-wizard';
import DatasetDrawer from './dataset-drawer';
import type { DataSourceService } from './datasource-service';

export type { DataSourceService } from './datasource-service';

const { Title } = Typography;

export interface DataSourceManagerProps {
  service: DataSourceService;
  pageSize?: number;
}

const DEFAULT_PAGE_SIZE = 10;

export default function DataSourceManager({
  service,
  pageSize = DEFAULT_PAGE_SIZE,
}: DataSourceManagerProps) {
  const { message } = AntdApp.useApp();
  const [list, setList] = useState<DataSourceBrief[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardEditingId, setWizardEditingId] = useState<string | null>(null);

  const [dsDrawer, setDsDrawer] = useState<{ id: string; name: string } | null>(null);

  const refresh = useCallback(
    async (targetPage = page) => {
      setLoading(true);
      try {
        const res = await service.list(targetPage, pageSize);
        setList(res.list);
        setTotal(res.total);
      } catch (e) {
        message.error(`加载数据源列表失败: ${(e as Error).message}`);
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
    setWizardEditingId(null);
    setWizardOpen(true);
  };

  const openEdit = (record: DataSourceBrief) => {
    setWizardEditingId(record.id);
    setWizardOpen(true);
  };

  const handleDelete = async (id: string) => {
    try {
      await service.remove(id);
      message.success('数据源已删除');
      const remaining = list.length - 1;
      const targetPage = remaining === 0 && page > 1 ? page - 1 : page;
      setPage(targetPage);
      await refresh(targetPage);
    } catch (e) {
      message.error(`删除失败: ${(e as Error).message}`);
    }
  };

  const formatTime = (t: number) => (t ? new Date(t).toLocaleString() : '-');

  const columns: ColumnsType<DataSourceBrief> = useMemo(
    () => [
      { title: '名称', dataIndex: 'name', key: 'name' },
      { title: '类型', dataIndex: 'type', key: 'type', width: 120 },
      {
        title: '数据集',
        key: 'datasetCount',
        width: 110,
        render: (_, r) => (
          <a onClick={() => setDsDrawer({ id: r.id, name: r.name })}>{r.datasetCount ?? 0} 个</a>
        ),
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
        width: 140,
        render: (_, r) => (
          <Space size="middle">
            <a onClick={() => openEdit(r)}>编辑</a>
            <Popconfirm
              title="确认删除该数据源？"
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
          数据源管理
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建数据源
        </Button>
      </div>

      <Table<DataSourceBrief>
        rowKey="id"
        columns={columns}
        dataSource={list}
        loading={loading}
        locale={{ emptyText: <Empty description="暂无数据源，点右上「新建数据源」开始" /> }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: false,
          showTotal: (t) => `共 ${t} 条`,
          onChange: onPageChange,
        }}
      />

      <DataSourceWizard
        open={wizardOpen}
        editingId={wizardEditingId}
        service={service}
        onClose={() => setWizardOpen(false)}
        onSaved={() => {
          setPage(1);
          void refresh(1);
        }}
      />

      <DatasetDrawer
        open={!!dsDrawer}
        datasourceId={dsDrawer?.id}
        datasourceName={dsDrawer?.name}
        service={service}
        onClose={() => setDsDrawer(null)}
        onSaved={() => {
          setPage(1);
          void refresh(1);
        }}
      />
    </div>
  );
}
