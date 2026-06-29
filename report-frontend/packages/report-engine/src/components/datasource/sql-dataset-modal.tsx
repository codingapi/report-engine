import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Empty, Form, Input, Modal, Select, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DataSourceService, WizardField, WizardTable } from './datasource-service';

/** 业务数据类型选项（与后端 DataType 对齐） */
const DATA_TYPE_OPTIONS = ['STRING', 'NUMBER', 'DATE', 'DATETIME', 'BOOLEAN', 'JSON'].map((t) => ({
  label: t,
  value: t,
}));

export interface SqlDatasetModalProps {
  open: boolean;
  datasourceId: string;
  service: DataSourceService;
  /** 已有数据集标识名集合，用于唯一性校验（编辑模式排除自身） */
  existingNames: string[];
  /** 编辑模式：传入待编辑的 SQL 数据集（回填 name/alias/sql/字段）；新建模式为空 */
  editing?: WizardTable | null;
  onClose: () => void;
  onConfirm: (table: WizardTable) => void;
}

/**
 * 新建 SQL 数据集弹窗：录入数据集名 + 查询 SQL，「解析字段」执行 SQL 推断列，确认后产出一个数据集。
 *
 * <p>产出的 {@link WizardTable} 中 {@code name} 存整段 SQL（后端 sourceTable 以 SELECT 开头即当 SQL 执行）、
 * {@code alias} 为用户起的数据集名。字典转换等数据适配由用户在 SQL 内 JOIN 字典表完成。
 */
export default function SqlDatasetModal({
  open,
  datasourceId,
  service,
  existingNames,
  editing,
  onClose,
  onConfirm,
}: SqlDatasetModalProps) {
  const { message } = AntdApp.useApp();
  const isEdit = !!editing;
  const [name, setName] = useState('');
  const [alias, setAlias] = useState('');
  const [sql, setSql] = useState('');
  const [fields, setFields] = useState<WizardField[]>([]);
  const [parsing, setParsing] = useState(false);

  // 打开时按编辑/新建回填或清空
  useEffect(() => {
    if (!open) return;
    if (editing) {
      setName(editing.name);
      setAlias(editing.alias ?? '');
      setSql(editing.sourceTable ?? '');
      setFields(editing.columns);
    } else {
      setName('');
      setAlias('');
      setSql('');
      setFields([]);
    }
  }, [open, editing]);

  const reset = () => {
    setName('');
    setAlias('');
    setSql('');
    setFields([]);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleParse = async () => {
    if (!sql.trim()) {
      message.warning('请输入查询 SQL');
      return;
    }
    if (!service.introspectSql) {
      message.error('当前数据源不支持 SQL 解析');
      return;
    }
    setParsing(true);
    try {
      const cols = await service.introspectSql(datasourceId, sql.trim());
      setFields(
        cols.map((c) => ({
          name: c.name,
          alias: c.name,
          dataType: c.type,
          primaryKey: c.primaryKey,
        })),
      );
      message.success(`解析完成，共 ${cols.length} 个字段`);
    } catch (e) {
      message.error(`SQL 解析失败: ${(e as Error).message}`);
    } finally {
      setParsing(false);
    }
  };

  const handleOk = () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      message.warning('请输入数据集名称（标识名）');
      return;
    }
    // 编辑模式排除自身后校验唯一
    const others = isEdit ? existingNames.filter((n) => n !== editing!.name) : existingNames;
    if (others.includes(trimmedName)) {
      message.warning('已存在同名数据集，请换一个标识名');
      return;
    }
    if (!sql.trim()) {
      message.warning('请输入查询 SQL');
      return;
    }
    if (fields.length === 0) {
      message.warning('请先「解析字段」');
      return;
    }
    // 数据集统一 name + alias；SQL 单独存 sourceTable。编辑模式保留原 id（不断引用）。
    onConfirm({
      id: editing?.id,
      name: trimmedName,
      alias: alias.trim() || trimmedName,
      sourceTable: sql.trim(),
      columns: fields,
    });
    reset();
  };

  const updateFieldAlias = (fieldName: string, value: string) =>
    setFields((fs) => fs.map((f) => (f.name === fieldName ? { ...f, alias: value } : f)));

  const updateFieldType = (fieldName: string, value: string) =>
    setFields((fs) => fs.map((f) => (f.name === fieldName ? { ...f, dataType: value } : f)));

  const columns: ColumnsType<WizardField> = [
    { title: '字段名', dataIndex: 'name', width: 160 },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 130,
      render: (_v, r) => (
        <Select
          size="small"
          style={{ width: '100%' }}
          value={DATA_TYPE_OPTIONS.some((o) => o.value === r.dataType) ? r.dataType : 'STRING'}
          options={DATA_TYPE_OPTIONS}
          onChange={(v) => updateFieldType(r.name, v)}
        />
      ),
    },
    {
      title: '别名',
      dataIndex: 'alias',
      render: (_v, r) => (
        <Input
          size="small"
          value={r.alias}
          placeholder="可选"
          onChange={(e) => updateFieldAlias(r.name, e.target.value)}
        />
      ),
    },
  ];

  return (
    <Modal
      title={isEdit ? '编辑 SQL 数据集' : '新建 SQL 数据集'}
      open={open}
      onOk={handleOk}
      onCancel={handleClose}
      okText={isEdit ? '保存' : '添加'}
      cancelText="取消"
      width={720}
      destroyOnHidden
    >
      <Form layout="vertical">
        <Form.Item label="数据集名称（标识名）" required tooltip="数据集的英文/引用标识名，需唯一">
          <Input
            value={name}
            placeholder="如 student_score"
            onChange={(e) => setName(e.target.value)}
          />
        </Form.Item>
        <Form.Item label="别名" tooltip="中文名，面向用户展示；缺省用标识名">
          <Input
            value={alias}
            placeholder="如 学生成绩明细"
            onChange={(e) => setAlias(e.target.value)}
          />
        </Form.Item>
        <Form.Item
          label="查询 SQL"
          required
          tooltip="一段 SELECT 查询；可在此 JOIN 字典表完成数据适配。解析后按结果列推断字段。"
        >
          <Input.TextArea
            value={sql}
            placeholder="SELECT s.id, s.name, d.label AS status FROM t_student s LEFT JOIN t_dict d ON ..."
            autoSize={{ minRows: 4, maxRows: 10 }}
            onChange={(e) => setSql(e.target.value)}
          />
        </Form.Item>
        <Button onClick={handleParse} loading={parsing} style={{ marginBottom: 12 }}>
          解析字段
        </Button>
        <Table<WizardField>
          rowKey="name"
          size="small"
          columns={columns}
          dataSource={fields}
          pagination={false}
          locale={{ emptyText: <Empty description="点「解析字段」执行 SQL 推断列" /> }}
        />
      </Form>
    </Modal>
  );
}
