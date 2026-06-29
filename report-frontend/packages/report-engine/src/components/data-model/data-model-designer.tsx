import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useState } from 'react';
import { Alert, App as AntdApp, ConfigProvider, Spin, Tabs } from 'antd';
import type {
  DataModelDataset,
  DataModelSource,
  DataSourceBrief,
  IntrospectedTable,
  RelationshipInfo,
  TransformItemInfo,
} from '@coding-report/report-api';
import type { Relationship } from '@/types';
import DatasetTab from './dataset-tab';
import UnionEditor from './union-editor';
import RelationTab from './relation-tab';
import TransformTab from './transform-tab';

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
  transforms?: TransformItemInfo[];
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
  /** 探查数据源下所有表/列（添加数据集二级联动第二级；保留兼容，当前不用） */
  introspectDatasets: (sourceId: string) => Promise<IntrospectedTable[]>;
  /** 列出数据源已保存的数据集（物理表 + SQL 一视同仁，添加数据集二级联动第二级） */
  listDatasourceDatasets: (sourceId: string) => Promise<DataModelDataset[]>;
}

/** 命令式句柄：容器（如全屏抽屉 header 的保存按钮）通过它触发保存 */
export interface DataModelDesignerHandle {
  /** 保存当前模型 */
  save: () => Promise<void>;
}

export interface DataModelDesignerProps {
  dataModelId: string;
  service: DataModelDesignerService;
  /** 模型加载/变更时通知容器（容器据此渲染 header 的标题信息） */
  onModelChange?: (model: DataModelDTO | null) => void;
  /** 只读模式（已发布模型查看）：禁用所有编辑操作，仅供查看 */
  readOnly?: boolean;
}

/** RelationshipInfo（无 id）→ Relationship（带 id，编辑期稳定 rowKey 用），保存时再剥离 */
function withLocalIds(list: RelationshipInfo[]): Relationship[] {
  return list.map((r, i) => ({ ...r, id: `rel-${i}` }));
}

/** Relationship → RelationshipInfo（剥离 id，对齐后端 DTO） */
function stripIds(list: Relationship[]): RelationshipInfo[] {
  return list.map(({ id: _id, ...rest }) => rest);
}

/**
 * 数据模型设计器：加载/编辑/保存模型，三 tab（数据集 / 数据合集 / 关系）。
 *
 * 标题栏与保存按钮**不在本组件内渲染**——由容器（如全屏抽屉）通过
 * {@link DataModelDesignerHandle.save} 与 {@link DataModelDesignerProps.onModelChange}
 * 自行渲染到 header，避免出现两层标题栏。本组件只负责主体内容。
 */
const DataModelDesigner = forwardRef<DataModelDesignerHandle, DataModelDesignerProps>(
  ({ dataModelId, service, onModelChange, readOnly = false }, ref) => {
    const { message } = AntdApp.useApp();
    const [loading, setLoading] = useState(false);
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

    // 模型加载/变更 → 通知容器渲染 header 标题
    useEffect(() => {
      onModelChange?.(model);
    }, [model, onModelChange]);

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

    const handleTransformsChange = useCallback((transforms: TransformItemInfo[]) => {
      setModel((prev) => (prev ? { ...prev, transforms } : prev));
    }, []);

    const handleSave = useCallback(async () => {
      if (!model) return;
      try {
        await service.saveDataModel(model);
        message.success('保存成功');
      } catch (err: unknown) {
        message.error(`保存失败：${err instanceof Error ? err.message : String(err)}`);
      }
    }, [model, service, message]);

    useImperativeHandle(ref, () => ({ save: handleSave }), [handleSave]);

    if (loading || !model) {
      return (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin tip="加载中…" />
        </div>
      );
    }

    return (
      <ConfigProvider componentDisabled={readOnly}>
        <div style={{ padding: 16 }}>
          {readOnly && (
            <Alert
              type="info"
              showIcon
              banner
              message="该数据模型已发布，当前为只读查看模式。如需修改请先「转草稿」。"
              style={{ marginBottom: 12 }}
            />
          )}
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
                  readOnly={readOnly}
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
                  readOnly={readOnly}
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
                  readOnly={readOnly}
                />
              ),
            },
            {
              key: 'transforms',
              label: '转换项',
              children: (
                <TransformTab
                  transforms={model.transforms ?? []}
                  onChange={handleTransformsChange}
                  readOnly={readOnly}
                />
              ),
            },
          ]}
          />
        </div>
      </ConfigProvider>
    );
  },
);

DataModelDesigner.displayName = 'DataModelDesigner';

export default DataModelDesigner;
