import type { DataSourceConfig, DatasourceService } from '../types';
/**
 * 数据源 CRUD hook（接口先定义、API 调用由 #30 接入）。
 *
 * 通过 service 注入实现，本包不直接 import report-api 的 HTTP 客户端；
 * service 各方法可选，未注入时对应动作会被拒（抛错或返回 null）。
 */
export declare function useDatasource(service?: DatasourceService): {
    list: DataSourceConfig[];
    loading: boolean;
    error: Error | null;
    refresh: () => Promise<void>;
    create: (config: Omit<DataSourceConfig, "id">) => Promise<string | null>;
    update: (config: DataSourceConfig) => Promise<boolean>;
    remove: (id: string) => Promise<boolean>;
};
