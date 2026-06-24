import type { CellBinding, ReportValue } from '@/types';
import type { RenderBindingDTO, RenderValueDTO } from '@coding-report/report-api';

/** 强类型 ReportValue → 弱类型 RenderValueDTO（递归映射，对齐后端 Value 契约）。 */
export function toValueDTO(value: ReportValue): RenderValueDTO {
  return {
    type: value.type,
    payload: value.payload,
    aggregation: value.aggregation,
    operand: value.operand ? toValueDTO(value.operand) : undefined,
    funcName: value.funcName,
    args: value.args?.map(toValueDTO),
    parts: value.parts?.map((p) => ({
      kind: p.kind,
      text: p.text,
      value: p.value ? toValueDTO(p.value) : undefined,
    })),
  };
}

/** CellBinding → RenderBindingDTO（剥离设计态 displayText，附带 preview/drill 字段）。 */
export function toBindingDTO(binding: CellBinding): RenderBindingDTO {
  return {
    cellKey: binding.cellKey,
    value: toValueDTO(binding.value),
    expansion: binding.expansion,
    expandMode: binding.expandMode,
    mergeRepeated: binding.mergeRepeated,
    parentCell: binding.parentCell,
    conditions: binding.conditions.map((c) => ({
      id: c.id,
      left: toValueDTO(c.left),
      operator: c.operator,
      right: c.right ? toValueDTO(c.right) : null,
    })),
    independent: binding.independent ?? false,
    preview: binding.preview,
    drillEnabled: binding.drillEnabled,
    drillView: binding.drillView,
  };
}
