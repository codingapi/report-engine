import { useMemo } from 'react';
import { DataModelListPage } from '@coding-report/report-engine';
import type { DataModelService, DataModelDesignerService } from '@coding-report/report-engine';
import type { DataModelField } from '@coding-report/report-api';
import {
  createDataModel,
  deleteDataModel,
  getDataModel,
  getDataSource,
  introspectDatasets,
  listDataModelsPage,
  listDataSources,
  publishDataModel,
  unpublishDataModel,
  updateDataModel,
} from '@coding-report/report-api';

const service: DataModelService = {
  list: (current, pageSize) => listDataModelsPage(current, pageSize),
  save: (dto) => createDataModel(dto),
  remove: (id) => deleteDataModel(id),
  publish: (id) => publishDataModel(id),
  unpublish: (id) => unpublishDataModel(id),
};

/**
 * 数据模型管理页：列表 + 全屏抽屉设计器（库包 DataModelListPage 内置抽屉）。
 *
 * designerService 实现搬自原独立设计页：getDataModel 拼 brief+详情，
 * saveDataModel 按 id 走 update/create，listDataSources/introspectDatasets 透传 report-api。
 */
const DataModelsPage = () => {
  const designerService = useMemo<DataModelDesignerService>(
    () => ({
      getDataModel: async (modelId) => {
        const page = await listDataModelsPage(1, 1000);
        const brief = page.list.find((b) => b.id === modelId);
        const info = await getDataModel(modelId);
        return {
          id: modelId,
          name: brief?.name ?? '',
          status: brief?.status,
          createTime: brief?.createTime,
          updateTime: brief?.updateTime,
          datasets: info.datasets ?? [],
          relationships: info.relationships ?? [],
          datasources: info.datasources,
          transforms: info.transforms ?? [],
        };
      },
      saveDataModel: async (dto) => {
        const payload = {
          id: dto.id,
          name: dto.name,
          status: dto.status,
          createTime: dto.createTime,
          updateTime: dto.updateTime,
          datasets: dto.datasets,
          relationships: dto.relationships,
          datasources: dto.datasources,
          transforms: dto.transforms,
        };
        if (dto.id) {
          await updateDataModel(dto.id, payload);
        } else {
          await createDataModel(payload);
        }
      },
      listDataSources: async () => {
        const page = await listDataSources(1, 1000);
        return page.list;
      },
      introspectDatasets: (sourceId: string) => introspectDatasets(sourceId),
      // 列出数据源已保存数据集（物理表 + SQL 一视同仁），映射为数据模型可消费形态
      listDatasourceDatasets: async (sourceId: string) => {
        const ds = await getDataSource(sourceId);
        return (ds.datasets ?? []).map((d) => ({
          id: d.id ?? d.name ?? d.sourceTable,
          name: d.name ?? d.sourceTable,
          alias: d.alias,
          kind: 'TABLE' as const,
          datasourceId: sourceId,
          sourceTable: d.sourceTable,
          fields: (d.fields ?? []).map((f) => ({
            name: f.name,
            alias: f.alias,
            dataType: f.dataType as DataModelField['dataType'],
            primaryKey: f.primaryKey,
          })),
        }));
      },
    }),
    [],
  );

  return <DataModelListPage service={service} designerService={designerService} />;
};

export default DataModelsPage;
