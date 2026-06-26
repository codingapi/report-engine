import { useMemo } from 'react';
import { App as AntdApp } from 'antd';
import { DataModelDesigner } from '@coding-report/report-engine';
import type { DataModelDesignerService } from '@coding-report/report-engine';
import {
  createDataModel,
  getDataModel,
  listDataModelsPage,
  updateDataModel,
} from '@coding-report/report-api';
import { useParams } from 'react-router-dom';

/**
 * 数据模型设计页：路由 /datamodels/:id。
 *
 * DataModelDesignerService 实现：
 * - getDataModel: listDataModelsPage 找到 brief（id+name），getDataModel 取详情，拼合为 DataModelDTO
 * - saveDataModel: 有 id 走 updateDataModel，无 id 走 createDataModel
 */
const DatamodelDesignPage = () => {
  const { id = '' } = useParams<{ id: string }>();
  const { message } = AntdApp.useApp();

  const service = useMemo<DataModelDesignerService>(
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
    }),
    [message],
  );

  return <DataModelDesigner dataModelId={id} service={service} />;
};

export default DatamodelDesignPage;
