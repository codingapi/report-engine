import { DataSourceManager } from '@coding-report/report-engine';
import type { DataSourceService } from '@coding-report/report-engine';
import {
  listDataSources,
  getDataSource,
  saveDataSource,
  deleteDataSource,
  introspectDatasets,
  introspectByConfig,
  uploadDataFile,
  testConnection,
} from '@coding-report/report-api';
import { listDataSourceTypes } from '@coding-report/report-api';

const service: DataSourceService = {
  list: (current, pageSize) => listDataSources(current, pageSize),
  get: (id) => getDataSource(id),
  save: (dto) => saveDataSource(dto),
  remove: (id) => deleteDataSource(id),
  introspect: (id) => introspectDatasets(id),
  introspectByConfig: (dto) => introspectByConfig(dto),
  uploadDataFile: (file, type) => uploadDataFile(file, type),
  testConnection: (dto) => testConnection(dto),
  listDriverTypes: async () => {
    const res = await listDataSourceTypes(1, 100);
    return res.list;
  },
};

const DataSourcesPage = () => <DataSourceManager service={service} />;

export default DataSourcesPage;
