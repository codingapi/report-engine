import React, { useState } from 'react';
import { Button, Input, Select, Popconfirm, Empty, Tabs } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { LoopBlock, Dataset } from '../../types';
import { describeRange } from '../../utils/excel-cell';
import { datasetOptions, fieldOptions } from '../../utils/dataset-options';
import SectionLabel from './section-label';
import ConditionEditor from './condition-editor';

interface LoopBlockManagerProps {
  loopBlocks: LoopBlock[];
  datasets: Dataset[];
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
  onUpdate: (patch: Partial<LoopBlock>) => void;
  onDelete: () => void;
}> = ({ lb, datasets, allLoops, onUpdate, onDelete }) => {
  return (
    <div className="re-prop-loop-item__body">
      {/* 名称 */}
      <SectionLabel text="循环块名称" hint="自定义名称，用于标识此循环块（如：员工薪资条、订单明细）" />
      <Input
        size="small"
        value={lb.label}
        onChange={(e) => onUpdate({ label: e.target.value })}
        placeholder="输入名称，如：员工薪资条"
        style={{ marginBottom: 8 }}
      />

      {/* 模板区域（由右键选区确定，只读） */}
      <SectionLabel
        text="模板区域"
        hint="循环块在表格中覆盖的矩形区域，由创建时选中的区域决定。"
      />
      <Input
        size="small"
        value={describeRegion(lb)}
        readOnly
        style={{ marginBottom: 8, background: '#f5f5f5' }}
      />

      {/* 驱动数据集 */}
      <SectionLabel
        text="驱动数据集"
        hint="循环的数据来源。数据集有多少行（或多少分组），循环就执行多少次。"
      />
      <Select
        size="small"
        value={lb.source.datasetId || undefined}
        onChange={(dsId) => onUpdate({ source: { ...lb.source, datasetId: dsId } })}
        placeholder="选择驱动数据集"
        style={{ width: '100%', marginBottom: 8 }}
        options={datasetOptions(datasets)}
      />

      {/* 分组字段 */}
      <SectionLabel
        text="分组字段"
        hint="指定后按分组去重迭代（如按部门循环），不指定则逐行迭代（如每个员工一次）。"
      />
      <Select
        size="small"
        mode="multiple"
        value={lb.source.groupBy}
        onChange={(groupBy) => onUpdate({ source: { ...lb.source, groupBy } })}
        placeholder="不分组（逐行迭代）"
        style={{ width: '100%', marginBottom: 8 }}
        options={fieldOptions(datasets, lb.source.datasetId)}
      />

      {/* 排序字段 */}
      <SectionLabel text="排序字段" hint="控制循环迭代的输出顺序，可多选。" />
      <Select
        size="small"
        mode="multiple"
        value={lb.source.orderBy}
        onChange={(orderBy) => onUpdate({ source: { ...lb.source, orderBy } })}
        placeholder="不排序（按原始顺序）"
        style={{ width: '100%', marginBottom: 8 }}
        options={fieldOptions(datasets, lb.source.datasetId)}
      />

      {/* 过滤条件 */}
      <SectionLabel
        text="过滤条件"
        hint="只迭代满足条件的行（如 status = 在职）。多个条件之间为 AND 关系。"
      />
      <ConditionEditor
        conditions={lb.source.filters}
        datasets={datasets}
        loopBlocks={allLoops}
        onChange={(filters) => onUpdate({ source: { ...lb.source, filters } })}
      />

      {/* 删除 */}
      <div className="re-prop-actions">
        <Popconfirm
          title="删除此循环块？"
          description="删除后块内的循环字段引用将失效"
          onConfirm={onDelete}
          okText="删除"
          cancelText="取消"
        >
          <Button type="text" size="small" danger icon={<DeleteOutlined />}>
            删除此循环块
          </Button>
        </Popconfirm>
      </div>
    </div>
  );
};

const LoopBlockManager: React.FC<LoopBlockManagerProps> = ({
  loopBlocks,
  datasets,
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
      {/* 功能说明 */}
      <div className="re-loop-intro">
        <p>
          循环块用于定义模板中需要<strong>重复渲染</strong>的区域。渲染时，该区域会按驱动数据集的行数（或分组数）复制多次。
        </p>
        <p>
          块内单元格通过<strong>循环字段</strong>引用当前迭代行的字段（属性面板中选「循环字段」即可）。
        </p>
        <p>
          新增循环块请在<strong>表格中选中区域</strong>，右键选择「设为循环块」。
        </p>
      </div>

      {loopBlocks.length > 0 ? (
        <Tabs
          type="card"
          size="small"
          activeKey={current}
          onChange={setActiveKey}
          items={loopBlocks.map((lb, i) => ({
            key: lb.id,
            label: lb.label || `循环块${i + 1}`,
            children: (
              <LoopBlockForm
                lb={lb}
                datasets={datasets}
                allLoops={loopBlocks}
                onUpdate={(patch) => handleUpdate(i, patch)}
                onDelete={() => handleDelete(i)}
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
