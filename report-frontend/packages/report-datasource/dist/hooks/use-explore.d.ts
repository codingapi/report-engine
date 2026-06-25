import type { ColumnInfo, DatasourceService, TableInfo } from '../types';
/**
 * 表/列探查 hook（依赖 DatasourceService.exploreTables / exploreColumns）。
 *
 * service 各方法可选；未注入时返回空数组并设 error。
 * sourceId 变化自动重新拉表列表；调用 selectTable(table) 触发列拉取。
 */
export declare function useExplore(service?: DatasourceService, sourceId?: string): {
    tables: TableInfo[];
    columns: ColumnInfo[];
    activeTable: string | null;
    loadingTables: boolean;
    loadingColumns: boolean;
    error: Error | null;
    refreshTables: () => Promise<void>;
    selectTable: (table: string | null) => void;
};
