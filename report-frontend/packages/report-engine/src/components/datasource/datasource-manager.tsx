import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Steps,
  Table,
  Typography,
  Upload,
} from 'antd';
import { PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type {
  DataSourceBrief,
  DataSourceDTO,
  DataSourceKind,
  DataFileUploadResult,
  DataSourceTypeBrief,
  IntrospectedTable,
  TestResult,
} from '@coding-report/report-api';

const { Title, Paragraph, Text } = Typography;

const KIND_OPTIONS: Array<{ label: string; value: DataSourceKind; desc: string }> = [
  { label: 'DB（JDBC）', value: 'DB', desc: '连接关系型数据库，通过驱动查询表与字段' },
  { label: 'EXCEL', value: 'EXCEL', desc: '上传 .xlsx 文件，每个 sheet 即一张表' },
  { label: 'CSV', value: 'CSV', desc: '上传 .csv 文件，整个文件即一张表' },
];

/**
 * 数据源管理对外依赖的服务注入（app-pc 用 report-api 实现）。
 * 组件本身不直接调 API，保持纯 UI 可复用。
 */
export interface DataSourceService {
  list: (current: number, pageSize: number) => Promise<{
    list: DataSourceBrief[];
    total: number;
  }>;
  get: (id: string) => Promise<DataSourceDTO>;
  save: (dto: DataSourceDTO) => Promise<string>;
  remove: (id: string) => Promise<void>;
  introspect: (id: string) => Promise<IntrospectedTable[]>;
  uploadDataFile: (file: File, type?: DataSourceKind) => Promise<DataFileUploadResult>;
  testConnection: (dto: DataSourceDTO) => Promise<TestResult>;
  /** DB 驱动下拉用：列出已注册的 DB 类型驱动 */
  listDriverTypes?: () => Promise<DataSourceTypeBrief[]>;
}

export interface DataSourceManagerProps {
  service: DataSourceService;
  pageSize?: number;
}

const DEFAULT_PAGE_SIZE = 10;

interface WizardState {
  kind: DataSourceKind;
  name: string;
  // DB
  typeConfigId?: string;
  url?: string;
  username?: string;
  password?: string;
  schema?: string;
  // 文件类
  filePath?: string;
  fileName?: string;
  // 解析结果
  tables: IntrospectedTable[];
  // 已保存 id（DB 在 step3 暂存后写入）
  savedId?: string;
}

const EMPTY_WIZARD: WizardState = {
  kind: 'DB',
  name: '',
  tables: [],
};

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
  const [current, setCurrent] = useState(0);
  const [wizard, setWizard] = useState<WizardState>(EMPTY_WIZARD);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [introspecting, setIntrospecting] = useState(false);
  const [drivers, setDrivers] = useState<DataSourceTypeBrief[]>([]);
  const [form] = Form.useForm<{ name: string }>();

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

  const loadDrivers = useCallback(async () => {
    if (!service.listDriverTypes) return;
    try {
      const res = await service.listDriverTypes();
      setDrivers(res.filter((d) => d.kind === 'DB'));
    } catch (e) {
      message.error(`加载驱动列表失败: ${(e as Error).message}`);
    }
  }, [service, message]);

  const resetWizard = () => {
    setWizardOpen(false);
    setCurrent(0);
    setWizard(EMPTY_WIZARD);
    form.resetFields();
  };

  const openCreate = () => {
    resetWizard();
    setWizardOpen(true);
    form.setFieldsValue({ name: '' });
    void loadDrivers();
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
        width: 100,
        render: (_, r) => (
          <Popconfirm
            title="确认删除该数据源？"
            onConfirm={() => handleDelete(r.id)}
            okText="删除"
            cancelText="取消"
          >
            <a style={{ color: '#ff4d4f' }}>删除</a>
          </Popconfirm>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [list, page],
  );

  // ─── 向导：构造 DTO ─────────────────────────────
  const buildDTO = (): DataSourceDTO => {
    const config: Record<string, unknown> = {};
    if (wizard.kind === 'DB') {
      if (wizard.url) config.url = wizard.url;
      if (wizard.username) config.username = wizard.username;
      if (wizard.password) config.password = wizard.password;
      if (wizard.schema) config.schema = wizard.schema;
    } else {
      if (wizard.filePath) config.path = wizard.filePath;
    }
    return {
      id: wizard.savedId,
      name: wizard.name || '未命名数据源',
      type: wizard.kind,
      typeConfigId: wizard.kind === 'DB' ? wizard.typeConfigId : undefined,
      config,
    };
  };

  // ─── 步骤流转 ───────────────────────────────────
  const canNextFromStep0 = !!wizard.kind;
  const canNextFromStep1 = (() => {
    if (!wizard.name) return false;
    if (wizard.kind === 'DB') {
      return !!wizard.typeConfigId && !!wizard.url;
    }
    return !!wizard.filePath;
  })();

  const handleTestConnection = async () => {
    if (wizard.kind !== 'DB') return;
    setTesting(true);
    try {
      const result = await service.testConnection(buildDTO());
      if (result.ok) {
        message.success(result.message || '连接成功');
      } else {
        message.error(result.message || '连接失败');
      }
    } catch (e) {
      message.error(`连接测试失败: ${(e as Error).message}`);
    } finally {
      setTesting(false);
    }
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      const result = await service.uploadDataFile(file, wizard.kind);
      setWizard((s) => ({
        ...s,
        filePath: result.savedPath,
        fileName: file.name,
        tables: result.tables,
      }));
      message.success(`文件已上传：${file.name}`);
    } catch (e) {
      message.error(`文件上传失败: ${(e as Error).message}`);
    } finally {
      setUploading(false);
    }
  };

  const handleIntrospect = async () => {
    setIntrospecting(true);
    try {
      let id = wizard.savedId;
      let tables = wizard.tables;
      if (wizard.kind === 'DB') {
        // DB 必须先保存才能 introspect
        const dto = buildDTO();
        const isUpdate = !!wizard.savedId;
        id = await service.save(dto);
        if (isUpdate) {
          // 保存覆盖后密码字段若被脱敏回填为 ***，需要保留前端原值
        }
        tables = await service.introspect(id);
        setWizard((s) => ({ ...s, savedId: id, tables }));
      } else if (!tables.length) {
        // 文件类上传时已带 tables，理论上不会走到这；兜底重新解析
        if (!id) {
          const dto = buildDTO();
          id = await service.save(dto);
          tables = await service.introspect(id);
          setWizard((s) => ({ ...s, savedId: id, tables }));
        } else {
          tables = await service.introspect(id);
          setWizard((s) => ({ ...s, tables }));
        }
      }
      message.success(`解析完成，共 ${tables.length} 张表`);
    } catch (e) {
      message.error(`元数据解析失败: ${(e as Error).message}`);
    } finally {
      setIntrospecting(false);
    }
  };

  const handleFinalSave = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      let id = wizard.savedId;
      const dto = buildDTO();
      dto.name = values.name;
      if (id) {
        // 已在 step3 暂存，更新名称（如有变化）
        if (dto.name !== wizard.name) {
          id = await service.save(dto);
        }
      } else {
        // 文件类未暂存，最终保存
        id = await service.save(dto);
      }
      message.success(`数据源已保存：${id}`);
      resetWizard();
      await refresh(1);
      setPage(1);
    } catch (e) {
      if (e instanceof Error) {
        message.error(`保存失败: ${e.message}`);
      }
    } finally {
      setSaving(false);
    }
  };

  const next = async () => {
    if (current === 1) {
      // 进入 step2 前对 DB 自动跑一次连接测试（不强阻断，由用户手动测）
      if (wizard.kind === 'DB' && !wizard.tables.length) {
        // 不强制 introspect，等用户点击
      }
    }
    setCurrent((c) => Math.min(c + 1, 3));
  };
  const prev = () => setCurrent((c) => Math.max(c - 1, 0));

  // ─── 步骤渲染 ───────────────────────────────────
  const renderStep0 = () => (
    <div>
      <Paragraph type="secondary">选择数据源类型，决定后续配置形式。</Paragraph>
      <Radio.Group
        value={wizard.kind}
        onChange={(e) => setWizard((s) => ({ ...s, kind: e.target.value }))}
      >
        <Space direction="vertical">
          {KIND_OPTIONS.map((opt) => (
            <Radio key={opt.value} value={opt.value}>
              <Text strong>{opt.label}</Text>
              <Text type="secondary" style={{ marginLeft: 8 }}>
                {opt.desc}
              </Text>
            </Radio>
          ))}
        </Space>
      </Radio.Group>
    </div>
  );

  const renderStep1 = () => (
    <div>
      <Form form={form} layout="vertical" initialValues={{ name: wizard.name }}>
        <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
          <Input
            placeholder="如 本地 MySQL / 销售明细表"
            onChange={(e) => setWizard((s) => ({ ...s, name: e.target.value }))}
          />
        </Form.Item>
      </Form>

      {wizard.kind === 'DB' ? (
        <Form layout="vertical">
          <Form.Item label="驱动" required>
            <Select
              placeholder="选择已注册的 DB 驱动"
              value={wizard.typeConfigId}
              options={drivers.map((d) => ({ value: d.id, label: d.name }))}
              notFoundContent={drivers.length === 0 ? '尚无 DB 驱动，请先到「数据源类型」注册' : '无'}
              onChange={(v) => setWizard((s) => ({ ...s, typeConfigId: v }))}
            />
          </Form.Item>
          <Form.Item label="JDBC URL" required>
            <Input
              placeholder="jdbc:mysql://host:3306/db"
              value={wizard.url}
              onChange={(e) => setWizard((s) => ({ ...s, url: e.target.value }))}
            />
          </Form.Item>
          <Form.Item label="用户名">
            <Input
              autoComplete="off"
              value={wizard.username}
              onChange={(e) => setWizard((s) => ({ ...s, username: e.target.value }))}
            />
          </Form.Item>
          <Form.Item label="密码">
            <Input.Password
              autoComplete="new-password"
              value={wizard.password}
              onChange={(e) => setWizard((s) => ({ ...s, password: e.target.value }))}
            />
          </Form.Item>
          <Form.Item label="Schema（可选）">
            <Input
              placeholder="如 public / dbo"
              value={wizard.schema}
              onChange={(e) => setWizard((s) => ({ ...s, schema: e.target.value }))}
            />
          </Form.Item>
          <Form.Item>
            <Button loading={testing} onClick={handleTestConnection} disabled={!canNextFromStep1}>
              测试连接
            </Button>
          </Form.Item>
        </Form>
      ) : (
        <Form layout="vertical">
          <Form.Item label="文件" required>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Upload
                accept={wizard.kind === 'EXCEL' ? '.xlsx,.xls' : '.csv'}
                showUploadList={false}
                beforeUpload={(file) => {
                  void handleUpload(file);
                  return false;
                }}
                disabled={uploading}
              >
                <Button icon={<UploadOutlined />} loading={uploading}>
                  选择{wizard.kind === 'EXCEL' ? 'Excel' : 'CSV'}文件上传
                </Button>
              </Upload>
              {wizard.fileName && (
                <Text type="success" style={{ fontSize: 12 }}>
                  已上传: {wizard.fileName}（路径 {wizard.filePath}）
                </Text>
              )}
            </Space>
          </Form.Item>
        </Form>
      )}
    </div>
  );

  const renderStep2 = () => {
    if (!wizard.tables.length) {
      return (
        <div>
          <Paragraph type="secondary">
            {wizard.kind === 'DB'
              ? '需先保存连接才能解析元数据。点击「解析元数据」按钮，将当前配置暂存并调用后端 introspect。'
              : '文件上传时已带元数据；如需重新解析可点击下方按钮。'}
          </Paragraph>
          <Button
            type="primary"
            loading={introspecting}
            onClick={handleIntrospect}
            disabled={wizard.kind === 'DB' && !canNextFromStep1}
          >
            解析元数据
          </Button>
        </div>
      );
    }
    return (
      <div>
        <Space style={{ marginBottom: 12 }}>
          <Button loading={introspecting} onClick={handleIntrospect}>
            重新解析
          </Button>
          <Text type="secondary">共 {wizard.tables.length} 张表</Text>
        </Space>
        {wizard.tables.map((t) => (
          <Card
            key={t.name}
            size="small"
            title={t.name}
            style={{ marginBottom: 8 }}
            extra={<Text type="secondary">{t.columns.length} 列</Text>}
          >
            <Table<IntrospectedTable['columns'][number]>
              size="small"
              rowKey="name"
              pagination={false}
              dataSource={t.columns}
              columns={[
                { title: '字段', dataIndex: 'name', key: 'name' },
                { title: '类型', dataIndex: 'dataType', key: 'dataType', width: 160 },
                {
                  title: '主键',
                  dataIndex: 'primaryKey',
                  key: 'primaryKey',
                  width: 80,
                  render: (v: boolean) => (v ? '是' : '-'),
                },
              ]}
            />
          </Card>
        ))}
      </div>
    );
  };

  const renderStep3 = () => (
    <div>
      <Paragraph type="secondary">确认以下信息无误后保存。</Paragraph>
      <DescriptionsCard wizard={wizard} />
    </div>
  );

  const stepContents = [renderStep0(), renderStep1(), renderStep2(), renderStep3()];

  const stepDisabled = [
    !canNextFromStep0,
    !canNextFromStep1,
    wizard.tables.length === 0,
    false,
  ];

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
        locale={{
          emptyText: <Empty description="暂无数据源，点右上「新建数据源」开始" />,
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
        title="新建数据源"
        open={wizardOpen}
        onCancel={resetWizard}
        width={760}
        destroyOnHidden
        footer={
          <Space>
            <Button onClick={resetWizard}>取消</Button>
            <Button onClick={prev} disabled={current === 0 || saving}>
              上一步
            </Button>
            {current < 3 ? (
              <Button type="primary" onClick={next} disabled={stepDisabled[current]}>
                下一步
              </Button>
            ) : (
              <Button type="primary" loading={saving} onClick={handleFinalSave}>
                保存
              </Button>
            )}
          </Space>
        }
      >
        <Steps
          current={current}
          size="small"
          style={{ marginBottom: 24 }}
          items={[
            { title: '选类型' },
            { title: '配置' },
            { title: '解析预览' },
            { title: '保存' },
          ]}
        />
        {stepContents[current]}
      </Modal>
    </div>
  );
}

function DescriptionsCard({ wizard }: { wizard: WizardState }) {
  const items: Array<{ label: string; value: string }> = [
    { label: '名称', value: wizard.name || '-' },
    { label: '类型', value: wizard.kind },
  ];
  if (wizard.kind === 'DB') {
    items.push(
      { label: '驱动', value: wizard.typeConfigId || '-' },
      { label: 'JDBC URL', value: wizard.url || '-' },
      { label: '用户名', value: wizard.username || '-' },
      { label: 'Schema', value: wizard.schema || '-' },
    );
  } else {
    items.push(
      { label: '文件名', value: wizard.fileName || '-' },
      { label: '保存路径', value: wizard.filePath || '-' },
    );
  }
  items.push({ label: '解析表数', value: String(wizard.tables.length) });
  return (
    <Card size="small" bordered={false}>
      <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', rowGap: 8 }}>
        {items.map((it) => (
          <div key={it.label} style={{ display: 'contents' }}>
            <Text type="secondary">{it.label}</Text>
            <Text>{it.value}</Text>
          </div>
        ))}
      </div>
    </Card>
  );
}
