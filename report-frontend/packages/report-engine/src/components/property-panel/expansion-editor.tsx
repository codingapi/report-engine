import React from 'react';
import { Form, Radio, Select, Switch } from 'antd';
import type { CellBinding, Expansion, ExpandMode } from '@/types';
import { EXPANSION_LABELS } from '@/types';
import { parseCellKey, cellA1 } from '@/utils/excel-cell';

interface ExpansionEditorProps {
  expansion: Expansion;
  expandMode: ExpandMode;
  mergeRepeated: boolean;
  parentCell: string | null;
  independent: boolean;
  cellBindings: CellBinding[];
  currentCellKey: string;
  onChange: (
    patch: Partial<
      Pick<CellBinding, 'expansion' | 'expandMode' | 'mergeRepeated' | 'parentCell' | 'independent'>
    >,
  ) => void;
}

/** 将 cellKey "sheetId:row:col" 转为简短显示 */
function cellKeyLabel(key: string): string {
  if (key.split(':').length !== 3) return key;
  const { row, col } = parseCellKey(key);
  return cellA1(row, col);
}

const ExpansionEditor: React.FC<ExpansionEditorProps> = ({
  expansion,
  expandMode,
  mergeRepeated,
  parentCell,
  independent,
  cellBindings,
  currentCellKey,
  onChange,
}) => {
  const parentOptions = cellBindings
    .filter((b) => b.cellKey !== currentCellKey)
    .map((b) => ({
      value: b.cellKey,
      label: cellKeyLabel(b.cellKey),
    }));

  return (
    <Form layout="vertical" size="small">
      <Form.Item label="扩展方向" tooltip="数据从该单元格开始，沿指定方向依次铺开">
        <Radio.Group
          value={expansion}
          onChange={(e) => {
            const exp = e.target.value as Expansion;
            if (exp === 'NONE') {
              onChange({ expansion: exp, expandMode: 'LIST', mergeRepeated: false });
            } else {
              onChange({ expansion: exp });
            }
          }}
          optionType="button"
          buttonStyle="solid"
        >
          {Object.entries(EXPANSION_LABELS).map(([v, l]) => (
            <Radio.Button key={v} value={v}>
              {l}
            </Radio.Button>
          ))}
        </Radio.Group>
      </Form.Item>

      {expansion !== 'NONE' && (
        <Form.Item label="扩展模式" tooltip="明细：每行输出一条记录；分组：相同值只保留一行">
          <Radio.Group
            value={expandMode}
            onChange={(e) => {
              const mode = e.target.value as ExpandMode;
              if (mode === 'LIST') {
                onChange({ expandMode: mode, mergeRepeated: false });
              } else {
                onChange({ expandMode: mode });
              }
            }}
            optionType="button"
            buttonStyle="solid"
          >
            <Radio.Button value="LIST">明细列表</Radio.Button>
            <Radio.Button value="GROUP">分组去重</Radio.Button>
          </Radio.Group>
        </Form.Item>
      )}

      {expansion !== 'NONE' && expandMode === 'GROUP' && (
        <Form.Item
          label="合并重复值"
          tooltip="相邻相同值合并为一个跨行/跨列单元格（多级分组表头常用）"
        >
          <Switch
            size="small"
            checked={mergeRepeated}
            onChange={(checked) => onChange({ mergeRepeated: checked })}
          />
        </Form.Item>
      )}

      {expansion === 'VERTICAL' && (
        <Form.Item
          label="独立纵向带"
          tooltip="开启后本列不与同源列对齐，从本格起独立向下展开（交错排版）。注意：独立列无法再与其它列做跨列聚合/跨列汇总/主从合并。"
        >
          <Switch
            size="small"
            checked={independent}
            onChange={(checked) => onChange({ independent: checked })}
          />
        </Form.Item>
      )}

      {expansion !== 'NONE' && (
        <Form.Item
          label="父格"
          tooltip="多级分组时指定对齐参照格（父格变化时本行重置）。留空表示顶层，无父格依赖。"
        >
          <Select
            value={parentCell ?? undefined}
            onChange={(val) => onChange({ parentCell: val ?? null })}
            allowClear
            placeholder="无（顶层）"
            options={parentOptions}
            showSearch
            notFoundContent="无其他已绑定单元格"
          />
        </Form.Item>
      )}
    </Form>
  );
};

export default ExpansionEditor;
