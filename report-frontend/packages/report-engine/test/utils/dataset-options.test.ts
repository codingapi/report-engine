import { datasetOptions, fieldOptions } from '@/utils/dataset-options';
import type { Dataset } from '@/types';

const datasets: Dataset[] = [
  {
    id: 'ds1',
    alias: '员工表',
    fields: [
      { name: 'name', alias: '姓名', dataType: 'STRING' },
      { name: 'age', alias: '', dataType: 'NUMBER' }, // alias 为空，回退 name
    ],
  },
  { id: 'ds2', alias: '', fields: [] }, // alias 为空，回退 id
];

describe('datasetOptions', () => {
  test('value=数据集 id，label=别名（缺省回退 id）', () => {
    expect(datasetOptions(datasets)).toEqual([
      { value: 'ds1', label: '员工表' },
      { value: 'ds2', label: 'ds2' },
    ]);
  });
});

describe('fieldOptions', () => {
  test('非限定：value=裸字段名，label=别名（缺省回退 name）', () => {
    expect(fieldOptions(datasets, 'ds1')).toEqual([
      { value: 'name', label: '姓名' },
      { value: 'age', label: 'age' },
    ]);
  });
  test('限定：value="datasetId.field"', () => {
    expect(fieldOptions(datasets, 'ds1', true)).toEqual([
      { value: 'ds1.name', label: '姓名' },
      { value: 'ds1.age', label: 'age' },
    ]);
  });
  test('数据集不存在时返回空数组', () => {
    expect(fieldOptions(datasets, 'missing')).toEqual([]);
  });
});
