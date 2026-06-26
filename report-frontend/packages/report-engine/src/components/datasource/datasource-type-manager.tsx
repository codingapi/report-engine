import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
  Upload,
  message,
} from 'antd';
import { PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import type {
  DataSourceTypeBrief,
  DataSourceTypeDTO,
  DriverJarUploadResult,
} from '@coding-report/report-api';

const { Title } = Typography;

/**
 * 数据源类型管理对外依赖的服务注入（app-pc 用 report-api 实现）。
 * 组件本身不直接调 API，保持纯 UI 可复用。
 */
export interface DataSourceTypeService {
  list: (current: number, pageSize: number) => Promise<{
    list: DataSourceTypeBrief[];
    total: number;
  }>;
  get: (id: string) => Promise<DataSourceTypeDTO>;
  save: (dto: DataSourceTypeDTO) => Promise<string>;
  remove: (id: string) => Promise<void>;
  uploadDriverJar: (file: File) => Promise<DriverJarUploadResult>;
}

export interface DataSourceTypeManagerProps {
  service: DataSourceTypeService;
  pageSize?: number;
}

interface EditForm {
  id?: string;
  name: string;
  kind: string;
  jarFile: string;
  driverClass: string;
}

const DEFAULT_PAGE_SIZE = 10;

export default function DataSourceTypeManager({
  service,
  pageSize = DEFAULT_PAGE_SIZE,
}: DataSourceTypeManagerProps) {
  const [list, setList] = useState<DataSourceTypeBrief[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [driverClasses, setDriverClasses] = useState<string[]>([]);
  const [jarFile, setJarFile] = useState<string>('');
  const [form] = Form.useForm<EditForm>();

  const refresh = useCallback(
    async (targetPage = page) => {
      setLoading(true);
      try {
        const res = await service.list(targetPage, pageSize);
        setList(res.list);
        setTotal(res.total);
      } catch (e) {
        message.error(`加载数据源类型列表失败: ${(e as Error).message}`);
      } finally {
        setLoading(false);
      }
    },
    [page, pageSize, service],
  );

  useEffect(() => {
    refresh(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onPageChange = (next: number) => {
    setPage(next);
    refresh(next);
  };

  const resetModal = () => {
    setModalOpen(false);
    setEditingId(null);
    setDriverClasses([]);
    setJarFile('');
    setUploading(false);
    form.resetFields();
  };

  const openCreate = () => {
    resetModal();
    form.setFieldsValue({ kind: 'DB', name: '', jarFile: '', driverClass: '' });
    setModalOpen(true);
  };

  const openEdit = async (record: DataSourceTypeBrief) => {
    resetModal();
    setModalOpen(true);
    try {
      const dto = await service.get(record.id);
      setEditingId(record.id);
      setDriverClasses(dto.driverClass ? [dto.driverClass] : []);
      setJarFile(dto.jarFile ?? '');
      form.setFieldsValue({
        id: dto.id,
        name: dto.name,
        kind: dto.kind,
        jarFile: dto.jarFile,
        driverClass: dto.driverClass,
      });
    } catch (e) {
      message.error(`加载数据源类型详情失败: ${(e as Error).message}`);
      setModalOpen(false);
    }
  };

  const handleUpload = async (arg: Parameters<NonNullable<UploadProps['customRequest']>>[0]) => {
    const file = arg.file as File;
    if (!file) return;
    setUploading(true);
    try {
      const result = await service.uploadDriverJar(file);
      setJarFile(result.jarFile);
      setDriverClasses(result.driverClasses);
      form.setFieldValue('jarFile', result.jarFile);
      if (result.driverClasses.length > 0) {
        form.setFieldValue('driverClass', result.driverClasses[0]);
      }
      message.success('驱动 jar 上传成功');
    } catch (e) {
      message.error(`驱动 jar 上传失败: ${(e as Error).message}`);
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const dto: DataSourceTypeDTO = {
        id: editingId ?? undefined,
        name: values.name,
        kind: values.kind,
        jarFile: values.jarFile,
        driverClass: values.driverClass,
      };
      await service.save(dto);
      message.success(editingId ? '数据源类型已更新' : '数据源类型已创建');
      resetModal();
      const targetPage = editingId ? page : 1;
      await refresh(targetPage);
    } catch (e) {
      if (e instanceof Error) {
        message.error(`保存失败: ${e.message}`);
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await service.remove(id);
      message.success('数据源类型已删除');
      const remaining = list.length - 1;
      const targetPage = remaining === 0 && page > 1 ? page - 1 : page;
      setPage(targetPage);
      await refresh(targetPage);
    } catch (e) {
      message.error(`删除失败: ${(e as Error).message}`);
    }
  };

  const formatTime = (t: number) => (t ? new Date(t).toLocaleString() : '-');

  const columns: ColumnsType<DataSourceTypeBrief> = useMemo(
    () => [
      { title: '名称', dataIndex: 'name', key: 'name' },
      { title: '类型', dataIndex: 'kind', key: 'kind', width: 120 },
      {
        title: '驱动类',
        key: 'driverClass',
        render: (_, r) => {
          // 列表项未带 driverClass，编辑时从详情拿；这里展示 kind 即可
          return r.kind ?? '-';
        },
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
              title="确认删除该数据源类型？"
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
          数据源类型管理
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建驱动
        </Button>
      </div>

      <Table<DataSourceTypeBrief>
        rowKey="id"
        columns={columns}
        dataSource={list}
        loading={loading}
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
        title={editingId ? '编辑数据源类型' : '新建数据源类型'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={resetModal}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
        width={520}
      >
        <Form form={form} layout="vertical" initialValues={{ kind: 'DB' }}>
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="jarFile" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="如 MySQL 驱动" />
          </Form.Item>
          <Form.Item
            label="类型"
            name="kind"
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select
              options={[
                { value: 'DB', label: 'DB（JDBC）' },
                { value: 'EXCEL', label: 'EXCEL' },
                { value: 'CSV', label: 'CSV' },
              ]}
            />
          </Form.Item>
          <Form.Item label="驱动 jar 包" required>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Upload
                accept=".jar"
                showUploadList={false}
                customRequest={handleUpload}
                disabled={uploading}
              >
                <Button icon={<UploadOutlined />} loading={uploading}>
                  选择 jar 文件上传
                </Button>
              </Upload>
              {jarFile && (
                <span style={{ color: '#52c41a', fontSize: 12 }}>
                  已上传: {jarFile}
                </span>
              )}
            </Space>
          </Form.Item>
          <Form.Item
            label="驱动类"
            name="driverClass"
            rules={[{ required: true, message: '请选择驱动类' }]}
          >
            <Select
              placeholder="上传 jar 后选择"
              options={driverClasses.map((c) => ({ value: c, label: c }))}
              notFoundContent={
                driverClasses.length === 0 ? '请先上传 jar 包' : '无候选驱动类'
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
