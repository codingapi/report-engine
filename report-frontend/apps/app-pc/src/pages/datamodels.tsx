import { DataModelListPage } from '@coding-report/report-engine';
import type { DataModelService } from '@coding-report/report-engine';
import {
  createDataModel,
  deleteDataModel,
  listDataModelsPage,
} from '@coding-report/report-api';

const service: DataModelService = {
  list: (current, pageSize) => listDataModelsPage(current, pageSize),
  save: (dto) => createDataModel(dto),
  remove: (id) => deleteDataModel(id),
};

const DataModelsPage = () => <DataModelListPage service={service} />;

export default DataModelsPage;
