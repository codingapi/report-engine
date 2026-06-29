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
import type { DataModelDataset, DataSourceBrief } from '@coding-report/report-api';
import { formatDatasetLabel } from '@/utils/dataset-label';
import type { DataModelDesignerService } from './data-model-designer';

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
  // 数据源已保存的数据集（物理表 + SQL 一视同仁）
  const [available, setAvailable] = useState<DataModelDataset[]>([]);
  const [availableLoading, setAvailableLoading] = useState(false);
  const [pickedIds, setPickedIds] = useState<string[]>([]);

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
      setPickedIds([]);
      setAvailable([]);
      if (!sourceId) return;
      let active = true;
      setAvailableLoading(true);
      service
        .listDatasourceDatasets(sourceId)
        .then((list) => {
          if (active) setAvailable(list);
        })
        .catch((err: unknown) => {
          if (active) {
            message.error(`加载数据集失败：${err instanceof Error ? err.message : String(err)}`);
          }
        })
        .finally(() => {
          if (active) setAvailableLoading(false);
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
    setAvailable([]);
    setPickedIds([]);
  };

  const handleConfirm = () => {
    if (!selectedSourceId) {
      message.warning('请先选择数据源');
      return;
    }
    if (pickedIds.length === 0) {
      message.warning('请至少选择一个数据集');
      return;
    }
    // 已添加的数据集（按 id）不重复添加
    const existingIds = new Set(datasets.map((d) => d.id));
    const picked = available.filter((d) => pickedIds.includes(d.id) && !existingIds.has(d.id));
    if (picked.length === 0) {
      message.warning('所选数据集均已添加');
      return;
    }
    const skipped = pickedIds.length - picked.length;
    onChange([...datasets, ...picked]);
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
        render: (_v, r) => formatDatasetLabel(r.alias, r.name),
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
  const datasetOptions = useMemo(
    () => available.map((d) => ({ label: formatDatasetLabel(d.alias, d.name), value: d.id })),
    [available],
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
              loading={availableLoading}
              placeholder={selectedSourceId ? '请选择数据集' : '请先选择数据源'}
              disabled={!selectedSourceId}
              value={pickedIds}
              onChange={setPickedIds}
              options={datasetOptions}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
