import React from 'react';
import { Button, Input, Select, Popconfirm, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { LoopBlock, Dataset } from '../../types';
import { genId, findDataset } from '../../types';
import SectionLabel from './section-label';
import ConditionEditor from './condition-editor';

interface LoopBlockManagerProps {
  loopBlocks: LoopBlock[];
  datasets: Dataset[];
  onChange: (loopBlocks: LoopBlock[]) => void;
}

/** 列号 → 字母（0→A, 25→Z, 26→AA） */
function colToLetter(col: number): string {
  let str = '';
  let c = col;
  while (c >= 0) {
    str = String.fromCharCode(65 + (c % 26)) + str;
    c = Math.floor(c / 26) - 1;
  }
  return str;
}

/** 区域描述：A1:C4 */
function describeRegion(lb: LoopBlock): string {
  return `${colToLetter(lb.startColumn)}${lb.startRow + 1}:${colToLetter(lb.endColumn)}${lb.endRow + 1}`;
}

const LoopBlockManager: React.FC<LoopBlockManagerProps> = ({
  loopBlocks,
  datasets,
  onChange,
}) => {
  const handleAdd = () => {
    const newBlock: LoopBlock = {
      id: genId(),
      label: `循环块 ${loopBlocks.length + 1}`,
      sheetId: 'sheet1',
      startRow: 0,
      startColumn: 0,
      endRow: 0,
      endColumn: 0,
      source: {
        datasetId: datasets[0]?.id || '',
        filters: [],
        groupBy: [],
        orderBy: [],
      },
    };
    onChange([...loopBlocks, newBlock]);
  };

  const handleUpdate = (index: number, patch: Partial<LoopBlock>) => {
    const next = [...loopBlocks];
    next[index] = { ...next[index], ...patch };
    onChange(next);
  };

  const handleDelete = (index: number) => {
    onChange(loopBlocks.filter((_, i) => i !== index));
  };

  return (
    <div>
      {/* 功能说明 */}
      <div className="re-loop-intro">
        <p>
          循环块用于定义模板中需要<strong>重复渲染</strong>的区域。渲染时，该区域会按驱动数据集的行数（或分组数）复制多次。
        </p>
        <p>
          块内单元格通过 <code>LoopFieldValue</code> 引用当前迭代行的字段。
        </p>
      </div>

      {loopBlocks.length > 0 ? (
        <div className="re-prop-loop-list">
          {loopBlocks.map((lb, i) => (
            <div key={lb.id} className="re-prop-loop-item">
              {/* 卡片头部：名称 + 删除 */}
              <div className="re-prop-loop-item__header">
                <div style={{ flex: 1 }}>
                  <SectionLabel text="循环块名称" hint="自定义名称，用于在 UI 中标识此循环块（如：员工薪资条、订单明细）" />
                  <Input
                    size="small"
                    value={lb.label}
                    onChange={(e) => handleUpdate(i, { label: e.target.value })}
                    placeholder="输入名称，如：员工薪资条"
                  />
                </div>
                <Popconfirm
                  title="删除此循环块？"
                  description="删除后块内的 LoopFieldValue 引用将失效"
                  onConfirm={() => handleDelete(i)}
                  okText="删除"
                  cancelText="取消"
                >
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </div>

              <div className="re-prop-loop-item__body">
                {/* 模板区域 */}
                <SectionLabel
                  text="模板区域"
                  hint="循环块在表格中覆盖的矩形区域（如 A1:C4 表示从 A1 到 C4 的区域）。模板设计完成后根据实际布局填写。"
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
                  onChange={(dsId) =>
                    handleUpdate(i, {
                      source: { ...lb.source, datasetId: dsId },
                    })
                  }
                  placeholder="选择驱动数据集"
                  style={{ width: '100%', marginBottom: 8 }}
                  options={datasets.map((ds) => ({
                    value: ds.id,
                    label: ds.alias || ds.id,
                  }))}
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
                  onChange={(groupBy) =>
                    handleUpdate(i, {
                      source: { ...lb.source, groupBy },
                    })
                  }
                  placeholder="不分组（逐行迭代）"
                  style={{ width: '100%', marginBottom: 8 }}
                  options={
                    findDataset(datasets, lb.source.datasetId)?.fields.map((f) => ({
                      value: f.name,
                      label: f.alias || f.name,
                    })) || []
                  }
                />

                {/* 排序字段 */}
                <SectionLabel
                  text="排序字段"
                  hint="控制循环迭代的输出顺序，可多选。"
                />
                <Select
                  size="small"
                  mode="multiple"
                  value={lb.source.orderBy}
                  onChange={(orderBy) =>
                    handleUpdate(i, {
                      source: { ...lb.source, orderBy },
                    })
                  }
                  placeholder="不排序（按原始顺序）"
                  style={{ width: '100%', marginBottom: 8 }}
                  options={
                    findDataset(datasets, lb.source.datasetId)?.fields.map((f) => ({
                      value: f.name,
                      label: f.alias || f.name,
                    })) || []
                  }
                />

                {/* 过滤条件 */}
                <SectionLabel
                  text="过滤条件"
                  hint="只迭代满足条件的行（如 status = 在职）。多个条件之间为 AND 关系。"
                />
                <ConditionEditor
                  conditions={lb.source.filters}
                  datasets={datasets}
                  onChange={(filters) =>
                    handleUpdate(i, { source: { ...lb.source, filters } })
                  }
                />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无循环块，点击下方按钮添加"
          style={{ margin: '16px 0' }}
        />
      )}

      <Button
        type="dashed"
        icon={<PlusOutlined />}
        onClick={handleAdd}
        block
      >
        添加循环块
      </Button>
    </div>
  );
};

export default LoopBlockManager;
