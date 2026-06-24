import {
    parseTemplate,
    templateToString,
    valueDisplayText,
} from '@/value-text';
import type { Dataset, LoopBlock, ReportParam, ReportValue } from '@/types';

const datasets: Dataset[] = [
    {
        id: 'ds',
        alias: '员工表',
        fields: [
            { name: 'name', alias: '姓名', dataType: 'STRING' },
            { name: 'salary', alias: '薪资', dataType: 'NUMBER' },
        ],
    },
];

const loopBlocks: LoopBlock[] = [
    {
        id: 'loop1',
        label: '员工循环',
        sheetId: 's1',
        startRow: 0, startColumn: 0, endRow: 2, endColumn: 1,
        source: { datasetId: 'ds', filters: [], groupBy: [], orderBy: [] },
    },
];

const params: ReportParam[] = [
    { id: 'p1', name: 'startDate', alias: '开始日期', dataType: 'DATE' },
];

describe('parseTemplate — 归约规则', () => {
    test('纯文本无洞 → Literal', () => {
        expect(parseTemplate('hello world')).toEqual({ type: 'Literal', payload: 'hello world' });
    });

    test('整串单个洞、无文本 → 裸 Value（不套 Template）', () => {
        expect(parseTemplate('${ds.name}')).toEqual({ type: 'FieldValue', payload: 'ds.name' });
        expect(parseTemplate('${SUM(ds.salary)}')).toEqual({
            type: 'Aggregate',
            aggregation: 'SUM',
            operand: { type: 'FieldValue', payload: 'ds.salary' },
        });
    });

    test('文本 + 洞混合 → Template', () => {
        const v = parseTemplate('合计 ${SUM(ds.salary)} 元');
        expect(v.type).toBe('Template');
        expect(v.parts?.filter((p) => p.kind === 'text').map((p) => p.text)).toEqual([
            '合计 ', ' 元',
        ]);
        expect(v.parts?.find((p) => p.kind === 'hole')?.value).toEqual({
            type: 'Aggregate',
            aggregation: 'SUM',
            operand: { type: 'FieldValue', payload: 'ds.salary' },
        });
    });

    test('多个洞与文本交替保持顺序', () => {
        const v = parseTemplate('${ds.name} - ${ds.salary}');
        expect(v.type).toBe('Template');
        expect(v.parts?.map((p) => (p.kind === 'text' ? p.text : `\${${p.value?.type}}`))).toEqual([
            '${FieldValue}', ' - ', '${FieldValue}',
        ]);
    });
});

describe('parseExpr — 洞内表达式分类', () => {
    test('${name} → NameRef', () => {
        expect(parseTemplate('${startDate}')).toEqual({ type: 'NameRef', payload: 'startDate' });
    });

    test('${d.field} → FieldValue', () => {
        expect(parseTemplate('${ds.name}')).toEqual({ type: 'FieldValue', payload: 'ds.name' });
    });

    test('${loop.field} 在 loopBlocks 中 → LoopFieldValue；否则降级 FieldValue', () => {
        expect(parseTemplate('${loop1.name}', loopBlocks)).toEqual({
            type: 'LoopFieldValue', payload: 'loop1.name',
        });
        // 无 loopBlocks 时按普通字段处理
        expect(parseTemplate('${loop1.name}')).toEqual({
            type: 'FieldValue', payload: 'loop1.name',
        });
    });

    test('聚合函数名大小写不敏感，统一为大写枚举', () => {
        expect(parseTemplate('${sum(ds.salary)}')).toEqual({
            type: 'Aggregate', aggregation: 'SUM',
            operand: { type: 'FieldValue', payload: 'ds.salary' },
        });
        expect(parseTemplate('${COUNT_DISTINCT(ds.name)}')).toEqual({
            type: 'Aggregate', aggregation: 'COUNT_DISTINCT',
            operand: { type: 'FieldValue', payload: 'ds.name' },
        });
    });

    test('非聚合函数 → FunctionCall，按深度/引号分割参数', () => {
        expect(parseTemplate('${format(ds.name)}')).toEqual({
            type: 'FunctionCall', funcName: 'format',
            args: [{ type: 'FieldValue', payload: 'ds.name' }],
        });
        // 嵌套括号
        expect(parseTemplate('${if(gt(ds.salary,100),ds.name,ds.salary)}')).toEqual({
            type: 'FunctionCall', funcName: 'if',
            args: [
                { type: 'FunctionCall', funcName: 'gt', args: [
                    { type: 'FieldValue', payload: 'ds.salary' },
                    { type: 'NameRef', payload: '100' },
                ]},
                { type: 'FieldValue', payload: 'ds.name' },
                { type: 'FieldValue', payload: 'ds.salary' },
            ],
        });
        // 字符串字面量内的逗号不分割
        expect(parseTemplate('${concat("a,b",ds.name)}')).toEqual({
            type: 'FunctionCall', funcName: 'concat',
            args: [
                { type: 'Literal', payload: 'a,b' },
                { type: 'FieldValue', payload: 'ds.name' },
            ],
        });
    });

    test('字符串字面量 → Literal（去引号）', () => {
        expect(parseTemplate('${"hello"}')).toEqual({ type: 'Literal', payload: 'hello' });
        expect(parseTemplate("${'hi'}")).toEqual({ type: 'Literal', payload: 'hi' });
    });
});

describe('templateToString — 可逆性', () => {
    const cases: Array<[string, string]> = [
        ['${ds.name}', '${ds.name}'],
        ['${SUM(ds.salary)}', '${SUM(ds.salary)}'],
        ['${format(ds.name, ds.salary)}', '${format(ds.name, ds.salary)}'],
        ['合计 ${SUM(ds.salary)} 元', '合计 ${SUM(ds.salary)} 元'],
        ['${startDate}', '${startDate}'],
    ];
    for (const [src, expected] of cases) {
        test(`往返保持源码：${src}`, () => {
            expect(templateToString(parseTemplate(src, loopBlocks))).toBe(expected);
        });
    }

    test('Literal 直接返回文本', () => {
        expect(templateToString({ type: 'Literal', payload: '纯文本' })).toBe('纯文本');
    });
});

describe('valueDisplayText — 别名友好展示', () => {
    test('Literal 直接显示文字（不含 ${}）', () => {
        expect(valueDisplayText({ type: 'Literal', payload: '标题' }, datasets)).toBe('标题');
    });

    test('FieldValue → ${数据集别名.字段别名}（取值类整体包 ${}）', () => {
        expect(valueDisplayText({ type: 'FieldValue', payload: 'ds.name' }, datasets)).toBe('${员工表.姓名}');
    });

    test('NameRef/ParamValue → ${参数别名}（缺失回退参数名）', () => {
        expect(valueDisplayText({ type: 'NameRef', payload: 'startDate' }, datasets, [], params)).toBe('${开始日期}');
        expect(valueDisplayText({ type: 'NameRef', payload: 'unknown' }, datasets, [], params)).toBe('${unknown}');
    });

    test('LoopFieldValue → ${循环标签.字段别名}', () => {
        expect(
            valueDisplayText({ type: 'LoopFieldValue', payload: 'loop1.name' }, datasets, loopBlocks),
        ).toBe('${员工循环.姓名}');
    });

    test('Aggregate → 聚合名(友好表达式)，整体包成 ${}', () => {
        const text = valueDisplayText({ type: 'Aggregate', aggregation: 'SUM', operand: { type: 'FieldValue', payload: 'ds.salary' } }, datasets);
        expect(text).toBe('${SUM(员工表.薪资)}');
    });

    test('Template → 文本段原样 + 洞包成 ${友好表达式}', () => {
        const v: ReportValue = {
            type: 'Template',
            parts: [
                { kind: 'text', text: '合计 ' },
                { kind: 'hole', value: { type: 'Aggregate', aggregation: 'SUM', operand: { type: 'FieldValue', payload: 'ds.salary' } } },
                { kind: 'text', text: ' 元' },
            ],
        };
        expect(valueDisplayText(v, datasets)).toBe('合计 ${SUM(员工表.薪资)} 元');
    });
});
