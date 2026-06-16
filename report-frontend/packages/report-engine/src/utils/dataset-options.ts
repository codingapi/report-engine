import type { Dataset } from '../types';
import { findDataset } from '../types';

export interface SelectOption {
  value: string;
  label: string;
}

/** 数据集下拉选项：value=数据集 id，label=别名。 */
export function datasetOptions(datasets: Dataset[]): SelectOption[] {
  return datasets.map((ds) => ({ value: ds.id, label: ds.alias || ds.id }));
}

/**
 * 字段下拉选项：label=字段别名。
 * @param qualified true → value 为全限定 "datasetId.field"（用于 FieldValue payload）；
 *                  false → value 为裸字段名（用于 groupBy/orderBy 等）。
 */
export function fieldOptions(datasets: Dataset[], datasetId: string, qualified = false): SelectOption[] {
  const ds = findDataset(datasets, datasetId);
  if (!ds) return [];
  return ds.fields.map((f) => ({
    value: qualified ? `${ds.id}.${f.name}` : f.name,
    label: f.alias || f.name,
  }));
}
