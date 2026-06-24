import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Table, Space, Popconfirm, Modal, Form, Input, Select, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  listReportConfigs,
  listDataModels,
  saveReportConfig,
  deleteReportConfig,
} from '@coding-report/report-api';
import type { ReportBrief, DataModelBrief } from '@coding-report/report-api';

const PAGE_SIZE = 10;

const ReportsPage = () => {
  const navigate = useNavigate();
  const [reports, setReports] = useState<ReportBrief[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [dataModels, setDataModels] = useState<DataModelBrief[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm<{ name: string; dataModelId: string }>();

  const refresh = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const [res, dms] = await Promise.all([
        listReportConfigs(targetPage, PAGE_SIZE),
        listDataModels(),
      ]);
      setReports(res.list);
      setTotal(res.total);
      setDataModels(dms);
    } catch (e) {
      message.error(`加载报表列表失败: ${e}`);
    } finally {
      setLoading(false);
    }
  }, [page]);

  const formatTime = (t?: number) => (t ? new Date(t).toLocaleString() : '-');

  useEffect(() => {
    refresh(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const dataModelName = (id?: string | null) => {
    if (!id) return '-';
    const dm = dataModels.find((d) => d.id === id);
    return dm ? dm.name : id;
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      const id = await saveReportConfig({
        name: values.name,
        dataModelId: values.dataModelId,
      });
      message.success('报表已创建');
      setCreateOpen(false);
      form.resetFields();
      // 新建后跳到末尾页可能更友好，这里直接刷新当前页并进入设计器
      await refresh(1);
      navigate(`/engine?id=${id}`);
    } catch (e) {
      if (e instanceof Error) {
        message.error(`创建报表失败: ${e}`);
      }
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteReportConfig(id);
      message.success('报表已删除');
      // 删除后若当前页空了且不在第一页，回退一页
      const remaining = reports.length - 1;
      const targetPage = remaining === 0 && page > 1 ? page - 1 : page;
      setPage(targetPage);
      await refresh(targetPage);
    } catch (e) {
      message.error(`删除报表失败: ${e}`);
    }
  };

  const openCreate = () => {
    form.resetFields();
    if (dataModels.length > 0) {
      form.setFieldsValue({ dataModelId: dataModels[0].id });
    }
    setCreateOpen(true);
  };

  const onPageChange = (next: number, _pageSize: number) => {
    setPage(next);
    refresh(next);
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>报表管理</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建报表
        </Button>
      </div>

      <Table
        rowKey="id"
        dataSource={reports}
        loading={loading}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          showTotal: (t) => `共 ${t} 条`,
          onChange: onPageChange,
        }}
        columns={[
          {
            title: '报表名称',
            dataIndex: 'name',
            key: 'name',
          },
          {
            title: '数据模型',
            key: 'dataModel',
            render: (_, record) => dataModelName(record.dataModelId),
          },
          {
            title: '创建时间',
            key: 'createTime',
            width: 180,
            render: (_, record) => formatTime(record.createTime),
          },
          {
            title: '更新时间',
            key: 'updateTime',
            width: 180,
            render: (_, record) => formatTime(record.updateTime),
          },
          {
            title: '操作',
            key: 'actions',
            width: 200,
            render: (_, record) => (
              <Space size="middle">
                <a onClick={() => navigate(`/engine?id=${record.id}`)}>编辑</a>
                <a onClick={() => navigate(`/preview?id=${record.id}`)}>预览</a>
                <Popconfirm
                  title="确认删除该报表？"
                  onConfirm={() => handleDelete(record.id)}
                  okText="删除"
                  cancelText="取消"
                >
                  <a style={{ color: '#ff4d4f' }}>删除</a>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="新建报表"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={creating}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="报表名称"
            rules={[{ required: true, message: '请输入报表名称' }]}
          >
            <Input placeholder="请输入报表名称" />
          </Form.Item>
          <Form.Item
            name="dataModelId"
            label="数据模型"
            rules={[{ required: true, message: '请选择数据模型' }]}
          >
            <Select
              placeholder="请选择数据模型"
              options={dataModels.map((dm) => ({ value: dm.id, label: dm.name }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ReportsPage;
