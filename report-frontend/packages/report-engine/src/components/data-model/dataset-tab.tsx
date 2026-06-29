import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Empty,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type {
  DataModelDataset,
  DataSourceBrief,
  IntrospectedTable,
} from '@coding-report/report-api';
import type { DataType } from '@/types';
import { formatDatasetLabel } from '@/utils/dataset-label';
import type { DataModelDesignerService } from './data-model-designer';

const DATA_TYPES: DataType[] = [
  'STRING',
  'NUMBER',
  'DATE',
  'DATETIME',
  'BOOLEAN',
  'JSON',
];

function normalizeDataType(raw: string): DataType {
  const upper = (raw ?? '').toUpperCase();
  return (DATA_TYPES as string[]).includes(upper) ? (upper as DataType) : 'STRING';
}

/** 将数据源 + 探查表转成 DataModelDataset（kind=TABLE，前端生成 id） */
function tableToDataset(
  source: DataSourceBrief,
  table: IntrospectedTable,
): DataModelDataset {
  return {
    // 表名即 id（稳定标识，与后端 DataSourceService.toTableDataset 兜底一致）：
    // 字段引用 payload = `${id}.field`，用表名才能渲染出「数据集名.字段名」而非随机串。
    id: table.name,
    alias: table.alias ?? table.name,
    kind: 'TABLE',
    datasourceId: source.id,
    sourceTable: table.name,
    fields: table.columns.map((c) => ({
      name: c.name,
      alias: c.remark || c.name,
      dataType: normalizeDataType(c.dataType),
      primaryKey: c.primaryKey,
    })),
  };
}

export interface DatasetTabProps {
  datasets: DataModelDataset[];
  onChange: (datasets: DataModelDataset[]) => void;
  service: DataModelDesignerService;
}

/**
 * 数据集 tab：二级联动多选添加数据集。
 *
 * 第一级：数据源（service.listDataSources）
 * 第二级：数据集（service.introspectDatasets，多选）
 *
 * 已选数据集以 Table 展示（数据源 / 数据集名 / 操作），模型只存 datasetId 引用。
 */
export default function DatasetTab({
  datasets,
  onChange,
  service,
}: DatasetTabProps) {
  const { message } = AntdApp.useApp();
  const [sources, setSources] = useState<DataSourceBrief[]>([]);
  const [sourcesLoading, setSourcesLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedSourceId, setSelectedSourceId] = useState<string | undefined>();
  const [tables, setTables] = useState<IntrospectedTable[]>([]);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [pickedTableNames, setPickedTableNames] = useState<string[]>([]);

  useEffect(() => {
    let active = true;
    setSourcesLoading(true);
    service
      .listDataSources()
      .then((list) => {
        if (active) setSources(list);
      })
      .catch((err: unknown) => {
        if (active) {
          message.error(`加载数据源列表失败：${err instanceof Error ? err.message : String(err)}`);
        }
      })
      .finally(() => {
        if (active) setSourcesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [service, message]);

  const sourceMap = useMemo(() => {
    const m = new Map<string, DataSourceBrief>();
    sources.forEach((s) => m.set(s.id, s));
    return m;
  }, [sources]);

  const handleSourceChange = useCallback(
    (sourceId: string) => {
      setSelectedSourceId(sourceId);
      setPickedTableNames([]);
      setTables([]);
      if (!sourceId) return;
      let active = true;
      setTablesLoading(true);
      service
        .introspectDatasets(sourceId)
        .then((list) => {
          if (active) setTables(list);
        })
        .catch((err: unknown) => {
          if (active) {
            message.error(`解析数据集失败：${err instanceof Error ? err.message : String(err)}`);
          }
        })
        .finally(() => {
          if (active) setTablesLoading(false);
        });
      return () => {
        active = false;
      };
    },
    [service, message],
  );

  const closeModal = () => {
    setModalOpen(false);
    setSelectedSourceId(undefined);
    setTables([]);
    setPickedTableNames([]);
  };

  const handleConfirm = () => {
    const source = selectedSourceId ? sourceMap.get(selectedSourceId) : undefined;
    if (!source) {
      message.warning('请先选择数据源');
      return;
    }
    if (pickedTableNames.length === 0) {
      message.warning('请至少选择一个数据集');
      return;
    }
    // 表名即 id，已存在的表不重复添加（同表不可添加两次）
    const existingIds = new Set(datasets.map((d) => d.id));
    const picked = tables.filter(
      (t) => pickedTableNames.includes(t.name) && !existingIds.has(t.name),
    );
    if (picked.length === 0) {
      message.warning('所选数据集均已添加');
      return;
    }
    const skipped = pickedTableNames.length - picked.length;
    const newDatasets = picked.map((t) => tableToDataset(source, t));
    onChange([...datasets, ...newDatasets]);
    if (skipped > 0) {
      message.info(`已跳过 ${skipped} 个已添加的数据集`);
    }
    closeModal();
  };

  const handleRemove = (id: string) => {
    onChange(datasets.filter((d) => d.id !== id));
  };

  const columns: ColumnsType<DataModelDataset> = useMemo(
    () => [
      {
        title: '数据集名',
        key: 'alias',
        render: (_v, r) => formatDatasetLabel(r.alias, r.sourceTable),
      },
      {
        title: '数据源',
        key: 'datasource',
        render: (_v, r) => {
          if (!r.datasourceId) return '-';
          const ds = sourceMap.get(r.datasourceId);
          if (!ds) return r.datasourceId;
          return `${ds.name} (${ds.type})`;
        },
      },
      {
        title: '操作',
        key: 'actions',
        width: 80,
        render: (_v, r) => (
          <Popconfirm
            title="确认移除该数据集？"
            onConfirm={() => handleRemove(r.id)}
            okText="移除"
            cancelText="取消"
          >
            <a style={{ color: '#ff4d4f' }}>移除</a>
          </Popconfirm>
        ),
      },
    ],
    [datasets, sourceMap],
  );

  const sourceOptions = useMemo(
    () => sources.map((s) => ({ label: `${s.name} (${s.type})`, value: s.id })),
    [sources],
  );
  const tableOptions = useMemo(
    () => tables.map((t) => ({ label: formatDatasetLabel(t.alias, t.name), value: t.name })),
    [tables],
  );

  return (
    <div>
      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'flex-end' }}>
        <Space>
          <Button
            icon={<PlusOutlined />}
            type="primary"
            onClick={() => setModalOpen(true)}
          >
            添加数据集
          </Button>
        </Space>
      </div>
      <Table<DataModelDataset>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={datasets}
        pagination={{
          pageSize: 10,
          size: 'small',
          showSizeChanger: false,
          showTotal: (t) => `共 ${t} 个数据集`,
        }}
        locale={{
          emptyText: <Empty description="暂无数据集，点上方「添加数据集」" />,
        }}
      />
      <Modal
        title="添加数据集"
        open={modalOpen}
        onOk={handleConfirm}
        onCancel={closeModal}
        okText="添加"
        cancelText="取消"
        destroyOnHidden
        width={680}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <div style={{ marginBottom: 4 }}>数据源</div>
            <Select
              style={{ width: '100%' }}
              loading={sourcesLoading}
              placeholder="请选择数据源"
              value={selectedSourceId}
              onChange={handleSourceChange}
              options={sourceOptions}
              showSearch
              optionFilterProp="label"
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>数据集（可多选）</div>
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              loading={tablesLoading}
              placeholder={selectedSourceId ? '请选择数据集' : '请先选择数据源'}
              disabled={!selectedSourceId}
              value={pickedTableNames}
              onChange={setPickedTableNames}
              options={tableOptions}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
