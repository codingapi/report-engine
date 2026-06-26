import { useCallback, useEffect, useMemo, useState } from 'react';
import { App as AntdApp, Button, Spin, Tabs, Typography, Space } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import type {
  DataModelDataset,
  DataModelSource,
  DataSourceBrief,
  IntrospectedTable,
  RelationshipInfo,
} from '@coding-report/report-api';
import type { Relationship } from '@/types';
import DatasetTab from './dataset-tab';
import UnionEditor from './union-editor';
import RelationTab from './relation-tab';

const { Title } = Typography;

/**
 * 数据模型设计页使用的完整 DTO。
 *
 * 后端两个端点拼合而成：
 * - {@code GET /api/datamodels} 列表项（DataModelBrief）提供 id/name
 * - {@code GET /api/datamodels/{id}} 详情（DataModelInfo）提供 datasets/relationships/datasources
 *
 * 设计页基于"已加载完整模型"的前提工作，故 id/name/datasets/relationships 必填。
 */
export interface DataModelDTO {
  id: string;
  name: string;
  status?: string;
  createTime?: number;
  updateTime?: number;
  datasets: DataModelDataset[];
  relationships: RelationshipInfo[];
  datasources?: DataModelSource[];
}

/**
 * 数据模型设计页对外依赖的服务注入（app-pc 用 report-api 实现）。
 * 组件本身不直接调 API，保持纯 UI 可复用。
 */
export interface DataModelDesignerService {
  /** 加载数据模型完整 DTO（id + name + datasets + relationships + datasources） */
  getDataModel: (id: string) => Promise<DataModelDTO>;
  /** 保存（新建/更新） */
  saveDataModel: (dto: DataModelDTO) => Promise<void>;
  /** 数据源列表（添加数据集二级联动第一级） */
  listDataSources: () => Promise<DataSourceBrief[]>;
  /** 探查数据源下所有表/列（添加数据集二级联动第二级） */
  introspectDatasets: (sourceId: string) => Promise<IntrospectedTable[]>;
}

export interface DataModelDesignerProps {
  dataModelId: string;
  service: DataModelDesignerService;
}

/** RelationshipInfo（无 id）→ Relationship（带 id，编辑期稳定 rowKey 用），保存时再剥离 */
function withLocalIds(list: RelationshipInfo[]): Relationship[] {
  return list.map((r, i) => ({ ...r, id: `rel-${i}` }));
}

/** Relationship → RelationshipInfo（剥离 id，对齐后端 DTO） */
function stripIds(list: Relationship[]): RelationshipInfo[] {
  return list.map(({ id: _id, ...rest }) => rest);
}

const formatTime = (t?: number) => (t ? new Date(t).toLocaleString() : '-');

/**
 * 数据模型设计页：顶部标题栏 + 三 tab（数据集 / 数据合集 / 关系）。
 * 数据集 tab 实现二级联动多选；其他 tab 暂为骨架。
 */
const DataModelDesigner: React.FC<DataModelDesignerProps> = ({
  dataModelId,
  service,
}) => {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [model, setModel] = useState<DataModelDTO | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    service
      .getDataModel(dataModelId)
      .then((dto) => {
        if (active) setModel(dto);
      })
      .catch((err: unknown) => {
        if (active) {
          message.error(`加载数据模型失败：${err instanceof Error ? err.message : String(err)}`);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataModelId, service, message]);

  const handleDatasetsChange = useCallback((datasets: DataModelDataset[]) => {
    setModel((prev) => (prev ? { ...prev, datasets } : prev));
  }, []);

  const editingRelationships = useMemo(
    () => (model ? withLocalIds(model.relationships) : []),
    [model?.relationships],
  );

  const handleRelationshipsChange = useCallback(
    (next: Relationship[]) => {
      setModel((prev) => (prev ? { ...prev, relationships: stripIds(next) } : prev));
    },
    [],
  );

  const handleSave = useCallback(async () => {
    if (!model) return;
    setSaving(true);
    try {
      await service.saveDataModel(model);
      message.success('保存成功');
    } catch (err: unknown) {
      message.error(`保存失败：${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setSaving(false);
    }
  }, [model, service, message]);

  if (loading || !model) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin tip="加载中…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Title level={4} style={{ margin: 0 }}>
          {model.name}
          <span
            style={{
              marginLeft: 12,
              fontSize: 12,
              color: 'rgba(0,0,0,0.45)',
              fontWeight: 'normal',
            }}
          >
            {model.status ? `（${model.status}）` : ''} 最后更新{' '}
            {formatTime(model.updateTime)}
          </span>
        </Title>
        <Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={handleSave}
          >
            保存
          </Button>
        </Space>
      </div>
      <Tabs
        items={[
          {
            key: 'datasets',
            label: '数据集',
            children: (
              <DatasetTab
                datasets={model.datasets}
                onChange={handleDatasetsChange}
                service={service}
              />
            ),
          },
          {
            key: 'unions',
            label: '数据合集',
            children: (
              <UnionEditor
                datasets={model.datasets}
                onChange={handleDatasetsChange}
              />
            ),
          },
          {
            key: 'relations',
            label: '关系',
            children: (
              <RelationTab
                datasets={model.datasets}
                relationships={editingRelationships}
                onChange={handleRelationshipsChange}
              />
            ),
          },
        ]}
      />
    </div>
  );
};

export default DataModelDesigner;
