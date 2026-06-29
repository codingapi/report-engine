import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import {
  App as AntdApp,
  Button,
  Descriptions,
  Drawer,
  Input,
  List,
  Radio,
  Select,
  Space,
  Spin,
  Steps,
  Typography,
  Upload,
} from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import type { DataSourceDTO, DataSourceKind, DataSourceTypeBrief } from '@coding-report/report-api';
import DatasetEditor from './dataset-editor';
import {
  fromDatasetDto,
  fromIntrospected,
  mergeTables,
  splitTableNames,
  tablesToDatasetDtos,
} from './datasource-service';
import type { DataSourceService, WizardTable } from './datasource-service';

const { Text, Paragraph } = Typography;

const KIND_OPTIONS: Array<{ label: string; value: DataSourceKind; desc: string }> = [
  { label: 'DB（JDBC）', value: 'DB', desc: '连接关系型数据库，通过驱动查询表与字段' },
  { label: 'EXCEL', value: 'EXCEL', desc: '上传 .xlsx 文件，每个 sheet 即一张表' },
  { label: 'CSV', value: 'CSV', desc: '上传 .csv 文件，整个文件即一张表' },
];

interface WizardState {
  kind: DataSourceKind;
  name: string;
  typeConfigId?: string;
  url?: string;
  username?: string;
  password?: string;
  schema?: string;
  filePath?: string;
  fileName?: string;
  tables: WizardTable[];
  savedId?: string;
}

const EMPTY_WIZARD: WizardState = { kind: 'DB', name: '', tables: [] };

export interface DataSourceWizardProps {
  open: boolean;
  /** 传 id 为编辑模式（回填配置 + 数据集），不传为新建 */
  editingId?: string | null;
  service: DataSourceService;
  onClose: () => void;
  onSaved: () => void;
}

export default function DataSourceWizard({
  open,
  editingId,
  service,
  onClose,
  onSaved,
}: DataSourceWizardProps) {
  const { message } = AntdApp.useApp();
  const editing = !!editingId;
  const [current, setCurrent] = useState(0);
  const [wizard, setWizard] = useState<WizardState>(EMPTY_WIZARD);
  const [drivers, setDrivers] = useState<DataSourceTypeBrief[]>([]);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [introspecting, setIntrospecting] = useState(false);
  // 指定解析的表名（逗号/空格分隔，空=全部）
  const [parseTableNames, setParseTableNames] = useState('');

  const loadDrivers = useCallback(async () => {
    if (!service.listDriverTypes) return;
    try {
      const res = await service.listDriverTypes();
      setDrivers(res.filter((d) => d.kind === 'DB'));
    } catch (e) {
      message.error(`加载驱动列表失败: ${(e as Error).message}`);
    }
  }, [service, message]);

  // 打开时初始化：新建重置 / 编辑回填
  useEffect(() => {
    if (!open) return;
    void loadDrivers();
    if (!editingId) {
      setWizard(EMPTY_WIZARD);
      setCurrent(0);
      return;
    }
    let active = true;
    setLoadingDetail(true);
    setCurrent(1);
    (async () => {
      try {
        const dto = await service.get(editingId);
        if (!active) return;
        const cfg = (dto.config ?? {}) as Record<string, string | undefined>;
        const path = cfg.path;
        setWizard({
          kind: dto.type,
          name: dto.name,
          typeConfigId: dto.typeConfigId,
          url: cfg.url,
          username: cfg.username,
          password: cfg.password,
          schema: cfg.schema,
          filePath: path,
          fileName: path ? path.split(/[\\/]/).pop() : undefined,
          tables: (dto.datasets ?? []).map(fromDatasetDto),
          savedId: dto.id,
        });
      } catch (e) {
        if (active) message.error(`加载数据源详情失败: ${(e as Error).message}`);
      } finally {
        if (active) setLoadingDetail(false);
      }
    })();
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editingId]);

  const buildDTO = (): DataSourceDTO => {
    const config: Record<string, unknown> = {};
    if (wizard.kind === 'DB') {
      if (wizard.url) config.url = wizard.url;
      if (wizard.username) config.username = wizard.username;
      if (wizard.password) config.password = wizard.password;
      if (wizard.schema) config.schema = wizard.schema;
    } else if (wizard.filePath) {
      config.path = wizard.filePath;
    }
    return {
      id: wizard.savedId,
      name: wizard.name || '未命名数据源',
      type: wizard.kind,
      typeConfigId: wizard.kind === 'DB' ? wizard.typeConfigId : undefined,
      config,
      datasets: tablesToDatasetDtos(wizard.tables, wizard.savedId),
    };
  };

  const canConfig =
    !!wizard.name &&
    (wizard.kind === 'DB' ? !!wizard.typeConfigId && !!wizard.url : !!wizard.filePath);

  const nextDisabled =
    current === 1 ? !canConfig : current === 2 ? wizard.tables.length === 0 : false;

  const handleTest = async () => {
    setTesting(true);
    try {
      const result = await service.testConnection(buildDTO());
      if (result.ok) message.success(result.message || '连接成功');
      else message.error(result.message || '连接失败');
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
        tables: mergeTables(s.tables, result.tables.map(fromIntrospected)),
      }));
      message.success(`文件已上传并解析：${file.name}`);
    } catch (e) {
      message.error(`文件上传失败: ${(e as Error).message}`);
    } finally {
      setUploading(false);
    }
  };

  const handleParse = async () => {
    setIntrospecting(true);
    try {
      // 解析不落库（避免半成品数据源），直接按当前配置探查；只有「保存」才落库
      // 指定表名时只解析这些表（空=全部）
      const names = splitTableNames(parseTableNames);
      const fresh = await service.introspectByConfig(buildDTO(), names);
      setWizard((s) => ({
        ...s,
        tables: mergeTables(s.tables, fresh.map(fromIntrospected)),
      }));
      message.success(`解析完成，共 ${fresh.length} 张表`);
    } catch (e) {
      message.error(`元数据解析失败: ${(e as Error).message}`);
    } finally {
      setIntrospecting(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const dto = buildDTO();
      await service.save(dto);
      message.success(`数据源已保存：${dto.name}`);
      onSaved();
      onClose();
    } catch (e) {
      message.error(`保存失败: ${(e as Error).message}`);
    } finally {
      setSaving(false);
    }
  };

  const field = (label: string, required: boolean, node: ReactNode) => (
    <div style={{ marginBottom: 16 }}>
      <Text strong>
        {required && <span style={{ color: '#ff4d4f', marginRight: 4 }}>*</span>}
        {label}
      </Text>
      <div style={{ marginTop: 8 }}>{node}</div>
    </div>
  );

  // ─── 步骤内容 ───────────────────────────────────
  const renderType = () => (
    <div>
      <Paragraph type="secondary">选择数据源类型，决定后续配置形式。</Paragraph>
      <Radio.Group
        value={wizard.kind}
        disabled={editing}
        onChange={(e) => setWizard((s) => ({ ...s, kind: e.target.value, tables: [] }))}
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

  const renderConfig = () => (
    <div>
      {field(
        '名称',
        true,
        <Input
          placeholder="如 本地 MySQL / 销售明细表"
          value={wizard.name}
          onChange={(e) => setWizard((s) => ({ ...s, name: e.target.value }))}
        />,
      )}
      {wizard.kind === 'DB' ? (
          <>
            {field(
              '驱动',
              true,
              <Select
                style={{ width: '100%' }}
                placeholder="选择已注册的 DB 驱动"
                value={wizard.typeConfigId}
                options={drivers.map((d) => ({ value: d.id, label: d.name }))}
                notFoundContent={
                  drivers.length === 0 ? '尚无 DB 驱动，请先到「数据库驱动」注册' : '无'
                }
                onChange={(v) => setWizard((s) => ({ ...s, typeConfigId: v }))}
              />,
            )}
            {field(
              'JDBC URL',
              true,
              <Input
                placeholder="jdbc:mysql://host:3306/db"
                value={wizard.url}
                onChange={(e) => setWizard((s) => ({ ...s, url: e.target.value }))}
              />,
            )}
            {field(
              '用户名',
              false,
              <Input
                autoComplete="off"
                value={wizard.username}
                onChange={(e) => setWizard((s) => ({ ...s, username: e.target.value }))}
              />,
            )}
            {field(
              '密码',
              false,
              <Input.Password
                autoComplete="new-password"
                value={wizard.password}
                onChange={(e) => setWizard((s) => ({ ...s, password: e.target.value }))}
              />,
            )}
            {field(
              'Schema（可选）',
              false,
              <Input
                placeholder="如 public / dbo"
                value={wizard.schema}
                onChange={(e) => setWizard((s) => ({ ...s, schema: e.target.value }))}
              />,
            )}
          </>
        ) : (
          field(
            '文件',
            true,
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
            </Space>,
          )
        )}
      </div>
    );

  const renderParse = () => (
    <DatasetEditor
      tables={wizard.tables}
      onChange={(tables) => setWizard((s) => ({ ...s, tables }))}
      toolbar={
        wizard.kind === 'DB' ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Text type="secondary">指定表名（多个用逗号或换行分隔，留空解析全部）</Text>
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 6 }}
              placeholder={'如：\nt_student\nt_student_class'}
              value={parseTableNames}
              onChange={(e) => setParseTableNames(e.target.value)}
              allowClear
            />
            <Space>
              <Button
                type="primary"
                loading={introspecting}
                onClick={handleParse}
                disabled={!canConfig}
              >
                {wizard.tables.length ? '重新解析表结构' : '解析表结构'}
              </Button>
              {wizard.tables.length > 0 && (
                <Text type="secondary">共 {wizard.tables.length} 张表</Text>
              )}
            </Space>
          </div>
        ) : (
          <Text type="secondary">
            上传文件后已自动解析，可在下方维护表/字段别名或删除不需要的表
          </Text>
        )
      }
      emptyHint={
        wizard.kind === 'DB' ? '点击上方「解析元数据」获取表结构' : '请回到「配置」步骤上传文件'
      }
    />
  );

  const renderPreview = () => {
    const driverName = drivers.find((d) => d.id === wizard.typeConfigId)?.name;
    return (
      <div>
        <Paragraph type="secondary">确认以下信息后点击右上「保存」。</Paragraph>
        <Descriptions
          column={1}
          size="small"
          bordered
          items={[
            { key: 'name', label: '名称', children: wizard.name || '-' },
            { key: 'type', label: '类型', children: wizard.kind },
            ...(wizard.kind === 'DB'
              ? [
                  { key: 'driver', label: '驱动', children: driverName || wizard.typeConfigId || '-' },
                  { key: 'url', label: 'JDBC URL', children: wizard.url || '-' },
                  { key: 'user', label: '用户名', children: wizard.username || '-' },
                  { key: 'schema', label: 'Schema', children: wizard.schema || '-' },
                ]
              : [{ key: 'file', label: '文件', children: wizard.fileName || wizard.filePath || '-' }]),
          ]}
        />
        <div style={{ marginTop: 16 }}>
          <Text strong>数据集（{wizard.tables.length}）</Text>
          <List
            size="small"
            dataSource={wizard.tables}
            locale={{ emptyText: '无数据集' }}
            renderItem={(t) => (
              <List.Item>
                <Text>{t.alias || t.name}</Text>
                <Text type="secondary" style={{ marginLeft: 8 }}>
                  源表 {t.name} · {t.columns.length} 字段
                </Text>
              </List.Item>
            )}
          />
        </div>
      </div>
    );
  };

  const steps = [renderType(), renderConfig(), renderParse(), renderPreview()];

  return (
    <Drawer
      title={editing ? '编辑数据源' : '新建数据源'}
      open={open}
      onClose={onClose}
      width={1040}
      destroyOnHidden
      extra={
        <Space>
          {current > 0 && (
            <Button onClick={() => setCurrent((c) => Math.max(c - 1, 0))}>上一步</Button>
          )}
          {wizard.kind === 'DB' && current === 1 && (
            <Button loading={testing} onClick={handleTest} disabled={!wizard.typeConfigId || !wizard.url}>
              测试连接
            </Button>
          )}
          {current < 3 ? (
            <Button
              type="primary"
              onClick={() => setCurrent((c) => Math.min(c + 1, 3))}
              disabled={nextDisabled}
            >
              下一步
            </Button>
          ) : (
            <Button type="primary" loading={saving} disabled={!canConfig} onClick={handleSave}>
              保存
            </Button>
          )}
          <Button onClick={onClose}>取消</Button>
        </Space>
      }
    >
      <Steps
        current={current}
        size="small"
        style={{ marginBottom: 24 }}
        items={[{ title: '类型' }, { title: '配置' }, { title: '解析' }, { title: '预览' }]}
      />
      <Spin spinning={loadingDetail}>{steps[current]}</Spin>
    </Drawer>
  );
}
