import {
    findDataset,
    findField,
    dataTypeLabel,
    DATA_TYPE_LABELS,
    genId,
} from '@/types';
import type { Dataset } from '@/types';

const datasets: Dataset[] = [
    {
        id: 'ds1', alias: '员工表',
        fields: [
            { name: 'name', alias: '姓名', dataType: 'STRING' },
            { name: 'age', alias: '年龄', dataType: 'NUMBER' },
        ],
    },
];

describe('findDataset', () => {
    test('按 id 命中', () => {
        expect(findDataset(datasets, 'ds1')?.alias).toBe('员工表');
    });
    test('未命中返回 undefined', () => {
        expect(findDataset(datasets, 'missing')).toBeUndefined();
    });
});

describe('findField', () => {
    test('按 "datasetId.field" 命中', () => {
        expect(findField(datasets, 'ds1.age')?.alias).toBe('年龄');
    });
    test('无点分隔 → null', () => {
        expect(findField(datasets, 'ds1')).toBeNull();
    });
    test('数据集不存在 → null', () => {
        expect(findField(datasets, 'missing.field')).toBeNull();
    });
    test('字段不存在 → null', () => {
        expect(findField(datasets, 'ds1.unknown')).toBeNull();
    });
});

describe('dataTypeLabel', () => {
    test('已知类型返回中文标签', () => {
        expect(dataTypeLabel('STRING')).toBe(DATA_TYPE_LABELS.STRING);
        expect(dataTypeLabel('NUMBER')).toBe(DATA_TYPE_LABELS.NUMBER);
    });
    test('未知类型回退原值', () => {
        expect(dataTypeLabel('UNKNOWN')).toBe('UNKNOWN');
    });
});

describe('genId', () => {
    test('格式前缀 r-', () => {
        expect(genId()).toMatch(/^r-/);
    });
    test('连续调用生成不同 id（计数器递增）', () => {
        const a = genId();
        const b = genId();
        expect(a).not.toBe(b);
    });
});
