import { toValueDTO, toBindingDTO } from '@/utils/render-dto';
import type { CellBinding, ReportValue } from '@/types';

describe('toValueDTO — 递归映射', () => {
  test('Literal 基本字段', () => {
    expect(toValueDTO({ type: 'Literal', payload: 'hi' })).toMatchObject({
      type: 'Literal',
      payload: 'hi',
    });
  });

  test('Aggregate 递归映射 operand', () => {
    const v: ReportValue = {
      type: 'Aggregate',
      aggregation: 'SUM',
      operand: { type: 'FieldValue', payload: 'ds.salary' },
    };
    expect(toValueDTO(v)).toEqual({
      type: 'Aggregate',
      aggregation: 'SUM',
      payload: undefined,
      funcName: undefined,
      args: undefined,
      parts: undefined,
      operand: { type: 'FieldValue', payload: 'ds.salary' },
    });
  });

  test('FunctionCall 映射 args 数组', () => {
    const v: ReportValue = {
      type: 'FunctionCall',
      funcName: 'format',
      args: [
        { type: 'FieldValue', payload: 'd.x' },
        { type: 'Literal', payload: 'fmt' },
      ],
    };
    expect(toValueDTO(v).args).toEqual([
      { type: 'FieldValue', payload: 'd.x' },
      { type: 'Literal', payload: 'fmt' },
    ]);
  });

  test('Template 映射 parts（text/hole）', () => {
    const v: ReportValue = {
      type: 'Template',
      parts: [
        { kind: 'text', text: 'A' },
        { kind: 'hole', value: { type: 'FieldValue', payload: 'd.x' } },
      ],
    };
    const dto = toValueDTO(v);
    expect(dto.parts).toEqual([
      { kind: 'text', text: 'A', value: undefined },
      { kind: 'hole', text: undefined, value: { type: 'FieldValue', payload: 'd.x' } },
    ]);
  });
});

describe('toBindingDTO — 剥离设计态 displayText', () => {
  test('剥离 displayText，保留权威字段与 preview/drill', () => {
    const binding: CellBinding = {
      cellKey: 's1:0:0',
      value: { type: 'FieldValue', payload: 'ds.name' },
      expansion: 'VERTICAL',
      expandMode: 'LIST',
      mergeRepeated: false,
      parentCell: null,
      conditions: [
        {
          id: 'c1',
          left: { type: 'FieldValue', payload: 'ds.age' },
          operator: 'GT',
          right: { type: 'Literal', payload: '18' },
        },
      ],
      independent: true,
      preview: '${员工表.姓名}',
      drillEnabled: true,
      drillView: 'ds2',
      displayText: '应被剥离',
    };
    const dto = toBindingDTO(binding);
    expect(dto).toMatchObject({
      cellKey: 's1:0:0',
      expansion: 'VERTICAL',
      expandMode: 'LIST',
      mergeRepeated: false,
      parentCell: null,
      independent: true,
      preview: '${员工表.姓名}',
      drillEnabled: true,
      drillView: 'ds2',
    });
    expect(dto).not.toHaveProperty('displayText');
    expect(dto.conditions[0]).toEqual({
      id: 'c1',
      left: { type: 'FieldValue', payload: 'ds.age' },
      operator: 'GT',
      right: { type: 'Literal', payload: '18' },
    });
    expect(dto.value).toMatchObject({ type: 'FieldValue', payload: 'ds.name' });
  });

  test('independent 缺省为 false', () => {
    const binding: CellBinding = {
      cellKey: 's1:0:0',
      value: { type: 'Literal', payload: 'x' },
      expansion: 'NONE',
      expandMode: 'GROUP',
      mergeRepeated: false,
      parentCell: null,
      conditions: [],
    };
    expect(toBindingDTO(binding).independent).toBe(false);
  });

  test('right 为 null 时保持 null', () => {
    const binding: CellBinding = {
      cellKey: 's1:0:0',
      value: { type: 'Literal', payload: 'x' },
      expansion: 'NONE',
      expandMode: 'GROUP',
      mergeRepeated: false,
      parentCell: null,
      conditions: [
        {
          id: 'c1',
          left: { type: 'FieldValue', payload: 'd.x' },
          operator: 'IS_NULL',
          right: null,
        },
      ],
    };
    expect(toBindingDTO(binding).conditions[0].right).toBeNull();
  });
});
