import { useCallback, useState } from 'react';
import type { DataSourceConfig, DatasourceService } from '@/types';

/**
 * 数据源 CRUD hook（接口先定义、API 调用由 #30 接入）。
 *
 * 通过 service 注入实现，本包不直接 import report-api 的 HTTP 客户端；
 * service 各方法可选，未注入时对应动作会被拒（抛错或返回 null）。
 */
export function useDatasource(service?: DatasourceService) {
  const [list, setList] = useState<DataSourceConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const refresh = useCallback(async () => {
    if (!service?.listDataSources) {
      setError(new Error('listDataSources 未注入'));
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await service.listDataSources();
      setList(data);
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, [service]);

  const create = useCallback(
    async (config: Omit<DataSourceConfig, 'id'>): Promise<string | null> => {
      if (!service?.createDataSource) {
        setError(new Error('createDataSource 未注入'));
        return null;
      }
      try {
        const id = await service.createDataSource(config);
        await refresh();
        return id;
      } catch (e) {
        setError(e as Error);
        return null;
      }
    },
    [service, refresh],
  );

  const update = useCallback(
    async (config: DataSourceConfig): Promise<boolean> => {
      if (!service?.updateDataSource) {
        setError(new Error('updateDataSource 未注入'));
        return false;
      }
      try {
        await service.updateDataSource(config);
        await refresh();
        return true;
      } catch (e) {
        setError(e as Error);
        return false;
      }
    },
    [service, refresh],
  );

  const remove = useCallback(
    async (id: string): Promise<boolean> => {
      if (!service?.deleteDataSource) {
        setError(new Error('deleteDataSource 未注入'));
        return false;
      }
      try {
        await service.deleteDataSource(id);
        await refresh();
        return true;
      } catch (e) {
        setError(e as Error);
        return false;
      }
    },
    [service, refresh],
  );

  return { list, loading, error, refresh, create, update, remove };
}
