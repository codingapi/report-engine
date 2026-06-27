import { useMemo } from 'react';
import { App as AntdApp } from 'antd';
import { DataModelListPage } from '@coding-report/report-engine';
import type { DataModelService, DataModelDesignerService } from '@coding-report/report-engine';
import {
  createDataModel,
  deleteDataModel,
  getDataModel,
  introspectDatasets,
  listDataModelsPage,
  listDataSources,
  updateDataModel,
} from '@coding-report/report-api';

const service: DataModelService = {
  list: (current, pageSize) => listDataModelsPage(current, pageSize),
  save: (dto) => createDataModel(dto),
  remove: (id) => deleteDataModel(id),
};

/**
 * 数据模型管理页：列表 + 全屏抽屉设计器（库包 DataModelListPage 内置抽屉）。
 *
 * designerService 实现搬自原独立设计页：getDataModel 拼 brief+详情，
 * saveDataModel 按 id 走 update/create，listDataSources/introspectDatasets 透传 report-api。
 */
const DataModelsPage = () => {
  const { message } = AntdApp.useApp();

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
        };
      },
      saveDataModel: async (dto) => {
        try {
          const payload = {
            id: dto.id,
            name: dto.name,
            status: dto.status,
            createTime: dto.createTime,
            updateTime: dto.updateTime,
            datasets: dto.datasets,
            relationships: dto.relationships,
            datasources: dto.datasources,
          };
          if (dto.id) {
            await updateDataModel(dto.id, payload);
          } else {
            await createDataModel(payload);
          }
          message.success('保存成功');
        } catch (err: unknown) {
          message.error(`保存失败：${err instanceof Error ? err.message : String(err)}`);
        }
      },
      listDataSources: async () => {
        const page = await listDataSources(1, 1000);
        return page.list;
      },
      introspectDatasets: (sourceId: string) => introspectDatasets(sourceId),
    }),
    [message],
  );

  return <DataModelListPage service={service} designerService={designerService} />;
};

export default DataModelsPage;
