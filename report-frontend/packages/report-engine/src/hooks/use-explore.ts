import { useCallback, useEffect, useState } from 'react';
import type { ColumnInfo, DatasourceService, TableInfo } from '@/types';

/**
 * 表/列探查 hook（依赖 DatasourceService.exploreTables / exploreColumns）。
 *
 * service 各方法可选；未注入时返回空数组并设 error。
 * sourceId 变化自动重新拉表列表；调用 selectTable(table) 触发列拉取。
 */
export function useExplore(service?: DatasourceService, sourceId?: string) {
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [columns, setColumns] = useState<ColumnInfo[]>([]);
  const [activeTable, setActiveTable] = useState<string | null>(null);
  const [loadingTables, setLoadingTables] = useState(false);
  const [loadingColumns, setLoadingColumns] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const refreshTables = useCallback(async () => {
    if (!sourceId) {
      setTables([]);
      return;
    }
    if (!service?.exploreTables) {
      setError(new Error('exploreTables 未注入'));
      setTables([]);
      return;
    }
    setLoadingTables(true);
    setError(null);
    try {
      const data = await service.exploreTables(sourceId);
      setTables(data);
    } catch (e) {
      setError(e as Error);
      setTables([]);
    } finally {
      setLoadingTables(false);
    }
  }, [service, sourceId]);

  const refreshColumns = useCallback(
    async (table: string) => {
      if (!sourceId || !table) {
        setColumns([]);
        return;
      }
      if (!service?.exploreColumns) {
        setError(new Error('exploreColumns 未注入'));
        setColumns([]);
        return;
      }
      setLoadingColumns(true);
      setError(null);
      try {
        const data = await service.exploreColumns(sourceId, table);
        setColumns(data);
      } catch (e) {
        setError(e as Error);
        setColumns([]);
      } finally {
        setLoadingColumns(false);
      }
    },
    [service, sourceId],
  );

  const selectTable = useCallback(
    (table: string | null) => {
      setActiveTable(table);
      if (table) void refreshColumns(table);
      else setColumns([]);
    },
    [refreshColumns],
  );

  useEffect(() => {
    void refreshTables();
    setActiveTable(null);
    setColumns([]);
  }, [refreshTables]);

  return {
    tables,
    columns,
    activeTable,
    loadingTables,
    loadingColumns,
    error,
    refreshTables,
    selectTable,
  };
}
