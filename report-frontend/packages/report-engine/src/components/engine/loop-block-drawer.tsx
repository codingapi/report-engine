import React from 'react';
import { Drawer, Tabs, Input, Tag, Button, Popconfirm, Empty } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { LoopBlockConfig } from '../properties/types';

export interface LoopBlockDrawerProps {
    open: boolean;
    onClose: () => void;
    loopBlocks: Record<string, LoopBlockConfig>;
    activeId?: string;
    onActiveChange: (id: string) => void;
    onLabelChange: (id: string, label: string) => void;
    onRemove: (id: string) => void;
}

const LoopBlockDrawer: React.FC<LoopBlockDrawerProps> = ({
    open,
    onClose,
    loopBlocks,
    activeId,
    onActiveChange,
    onLabelChange,
    onRemove,
}) => {
    const blockList = Object.values(loopBlocks);

    return (
        <Drawer
            title={`循环块管理${blockList.length > 0 ? ` (${blockList.length})` : ''}`}
            open={open}
            onClose={onClose}
            size={520}
        >
            {blockList.length === 0 ? (
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                        <span>
                            暂无循环块
                            <br />
                            <span style={{ fontSize: 12, color: '#999' }}>
                                在表格中选中多个单元格后右键 → 循环块 → 设置
                            </span>
                        </span>
                    }
                    style={{ marginTop: 48 }}
                />
            ) : (
                <Tabs
                    type="card"
                    activeKey={activeId}
                    onChange={onActiveChange}
                    items={blockList.map((block, index) => ({
                        key: block.id,
                        label: block.label || `循环块 ${index + 1}`,
                        children: (
                            <div style={{ padding: '8px 0' }}>
                                <Tag color="blue" style={{ marginBottom: 16 }}>
                                    行 {block.startRow + 1}–{block.endRow + 1}，列 {block.startColumn + 1}–{block.endColumn + 1}
                                </Tag>

                                <div style={{ marginBottom: 16 }}>
                                    <label style={{ fontSize: 12, color: '#666', display: 'block', marginBottom: 4 }}>
                                        循环块标签
                                    </label>
                                    <Input
                                        placeholder="如：部门循环、月份循环"
                                        value={block.label || ''}
                                        onChange={(e) => onLabelChange(block.id, e.target.value)}
                                    />
                                </div>

                                <Popconfirm
                                    title="确定移除该循环块？"
                                    onConfirm={() => onRemove(block.id)}
                                    okText="确定"
                                    cancelText="取消"
                                >
                                    <Button danger icon={<DeleteOutlined />}>
                                        移除循环块
                                    </Button>
                                </Popconfirm>
                            </div>
                        ),
                    }))}
                />
            )}
        </Drawer>
    );
};

export default LoopBlockDrawer;
