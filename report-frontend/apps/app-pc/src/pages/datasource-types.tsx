import { DataSourceTypeManager } from '@coding-report/report-engine';
import type { DataSourceTypeService } from '@coding-report/report-engine';
import {
  listDataSourceTypes,
  getDataSourceType,
  saveDataSourceType,
  deleteDataSourceType,
  uploadDriverJar,
} from '@coding-report/report-api';

const service: DataSourceTypeService = {
  list: (current, pageSize) => listDataSourceTypes(current, pageSize),
  get: (id) => getDataSourceType(id),
  save: (dto) => saveDataSourceType(dto),
  remove: (id) => deleteDataSourceType(id),
  uploadDriverJar: (file) => uploadDriverJar(file),
};

const DataSourceTypesPage = () => <DataSourceTypeManager service={service} />;

export default DataSourceTypesPage;
