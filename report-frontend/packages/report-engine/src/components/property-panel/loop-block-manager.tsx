import React, { useState } from 'react';
import { Form, Input, Select, Popconfirm, Empty, Tabs } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { LoopBlock, Dataset, ReportParam } from '@/types';
import { describeRange } from '@/utils/excel-cell';
import { datasetOptions, fieldOptions } from '@/utils/dataset-options';
import ConditionEditor from './condition-editor';

interface LoopBlockManagerProps {
  loopBlocks: LoopBlock[];
  datasets: Dataset[];
  params?: ReportParam[];
  onChange: (loopBlocks: LoopBlock[]) => void;
}

/** 区域描述：A1:C4 */
function describeRegion(lb: LoopBlock): string {
  return describeRange(lb.startRow, lb.startColumn, lb.endRow, lb.endColumn);
}

/** 单个循环块的配置表单 */
const LoopBlockForm: React.FC<{
  lb: LoopBlock;
  datasets: Dataset[];
  allLoops: LoopBlock[];
  params?: ReportParam[];
  onUpdate: (patch: Partial<LoopBlock>) => void;
}> = ({ lb, datasets, allLoops, params, onUpdate }) => {
  return (
    <Form layout="vertical" size="small" className="re-prop-loop-item__body">
      <Form.Item
        label="循环块名称"
        tooltip="自定义名称，用于标识此循环块（如：员工薪资条、订单明细）"
      >
        <Input
          value={lb.label}
          onChange={(e) => onUpdate({ label: e.target.value })}
          placeholder="输入名称，如：员工薪资条"
        />
      </Form.Item>

      <Form.Item label="模板区域" tooltip="循环块在表格中覆盖的矩形区域，由创建时选中的区域决定。">
        <Input value={describeRegion(lb)} readOnly style={{ background: '#f5f5f5' }} />
      </Form.Item>

      <Form.Item
        label="驱动数据集"
        tooltip="循环的数据来源。数据集有多少行（或多少分组），循环就执行多少次。"
      >
        <Select
          value={lb.source.datasetId || undefined}
          onChange={(dsId) => onUpdate({ source: { ...lb.source, datasetId: dsId } })}
          placeholder="选择驱动数据集"
          options={datasetOptions(datasets)}
        />
      </Form.Item>

      <Form.Item
        label="分组字段"
        tooltip="指定后按分组去重迭代（如按部门循环），不指定则逐行迭代（如每个员工一次）。"
      >
        <Select
          mode="multiple"
          value={lb.source.groupBy}
          onChange={(groupBy) => onUpdate({ source: { ...lb.source, groupBy } })}
          placeholder="不分组（逐行迭代）"
          options={fieldOptions(datasets, lb.source.datasetId)}
        />
      </Form.Item>

      <Form.Item label="排序字段" tooltip="控制循环迭代的输出顺序，可多选。">
        <Select
          mode="multiple"
          value={lb.source.orderBy}
          onChange={(orderBy) => onUpdate({ source: { ...lb.source, orderBy } })}
          placeholder="不排序（按原始顺序）"
          options={fieldOptions(datasets, lb.source.datasetId)}
        />
      </Form.Item>

      <Form.Item
        label="过滤条件"
        tooltip="只迭代满足条件的行（如 status = 在职）。多个条件之间为 AND 关系。"
      >
        <ConditionEditor
          conditions={lb.source.filters}
          datasets={datasets}
          loopBlocks={allLoops}
          params={params}
          onChange={(filters) => onUpdate({ source: { ...lb.source, filters } })}
        />
      </Form.Item>
    </Form>
  );
};

const LoopBlockManager: React.FC<LoopBlockManagerProps> = ({
  loopBlocks,
  datasets,
  params,
  onChange,
}) => {
  const [activeKey, setActiveKey] = useState<string | undefined>(undefined);

  const handleUpdate = (index: number, patch: Partial<LoopBlock>) => {
    const next = [...loopBlocks];
    next[index] = { ...next[index], ...patch };
    onChange(next);
  };

  const handleDelete = (index: number) => {
    onChange(loopBlocks.filter((_, i) => i !== index));
  };

  // activeKey 失效（删除/新建）时回落到最后一个块
  const keys = loopBlocks.map((l) => l.id);
  const current = activeKey && keys.includes(activeKey) ? activeKey : keys[keys.length - 1];

  return (
    <div>
      {loopBlocks.length > 0 ? (
        <Tabs
          type="card"
          size="small"
          activeKey={current}
          onChange={setActiveKey}
          items={loopBlocks.map((lb, i) => ({
            key: lb.id,
            label: (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                <span>{lb.label || `循环块${i + 1}`}</span>
                <Popconfirm
                  title="删除此循环块？"
                  description="删除后块内的循环字段引用将失效"
                  onConfirm={(e) => {
                    e?.stopPropagation();
                    handleDelete(i);
                  }}
                  onCancel={(e) => e?.stopPropagation()}
                  okText="删除"
                >
                  <DeleteOutlined
                    style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}
                    onClick={(e) => e.stopPropagation()}
                  />
                </Popconfirm>
              </span>
            ),
            children: (
              <LoopBlockForm
                lb={lb}
                datasets={datasets}
                allLoops={loopBlocks}
                params={params}
                onUpdate={(patch) => handleUpdate(i, patch)}
              />
            ),
          }))}
        />
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <span style={{ fontSize: 13, color: '#999' }}>
              暂无循环块
              <br />
              在表格中选中区域，右键「设为循环块」创建
            </span>
          }
          style={{ margin: '24px 0' }}
        />
      )}
    </div>
  );
};

export default LoopBlockManager;
