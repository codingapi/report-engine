import React from 'react';
import { Form, Switch, Select, Alert } from 'antd';
import type { Dataset } from '@/types';
import { datasetOptions } from '@/utils/dataset-options';

interface DrillEditorProps {
  drillEnabled?: boolean;
  drillView?: string | null;
  datasets: Dataset[];
  /** 推断的默认视图（该格字段所属数据集 id，可 null） */
  defaultView?: string | null;
  onChange: (patch: { drillEnabled?: boolean; drillView?: string | null }) => void;
}

/**
 * 反查配置编辑器：Switch 开启反查 + Select 选择反查视图（数据集）。
 * 默认关闭；开启后默认视图=该格字段所属数据集（由 defaultView 传入）。
 */
const DrillEditor: React.FC<DrillEditorProps> = ({
  drillEnabled,
  drillView,
  datasets,
  defaultView,
  onChange,
}) => {
  const enabled = drillEnabled ?? false;

  return (
    <Form layout="vertical" size="small">
      <Form.Item
        label="反查"
        tooltip="开启后，预览态下该格渲染为可点击（蓝色链接样式），用户点击可查看聚合/汇总计算的明细数据。仅对聚合格有意义。"
      >
        <Switch
          checked={enabled}
          onChange={(checked) => onChange({ drillEnabled: checked })}
        />
      </Form.Item>

      {enabled && (
        <Form.Item
          label="反查视图"
          tooltip="指定反查时展示哪个数据集的明细数据。默认（数据集本身）：回退到该格字段所属的数据集。"
        >
          <Select
            value={drillView ?? undefined}
            onChange={(val) => onChange({ drillView: val ?? null })}
            placeholder={`默认（${defaultView ? datasets.find(d => d.id === defaultView)?.alias || defaultView : '数据集本身'}）`}
            options={datasetOptions(datasets)}
            showSearch
            allowClear
          />
          <Alert
            type="info"
            showIcon={false}
            message="视图后续将与数据源一样独立管理配置；本期直接用数据集本身作默认视图。"
            style={{ marginTop: 8, fontSize: 12 }}
          />
        </Form.Item>
      )}
    </Form>
  );
};

export default DrillEditor;
