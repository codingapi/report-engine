import React, { useMemo } from 'react';
import { Empty, Input, Tag, Popconfirm, Button } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { findBlockAtCell } from '@coding-report/report-univer';
import type { DataConfig } from '../datasource/types';
import type { SelectedCellInfo, LoopBlockConfig } from './types';

interface LoopBlockSectionProps {
    selectedCell: SelectedCellInfo | null;
    dataConfig?: DataConfig;
    loopBlocks: Record<string, LoopBlockConfig>;
    onLoopBlockChange: (id: string, config: LoopBlockConfig) => void;
    onLoopBlockRemove: (id: string) => void;
}

const LoopBlockSection: React.FC<LoopBlockSectionProps> = ({
    selectedCell,
    dataConfig,
    loopBlocks,
    onLoopBlockChange,
    onLoopBlockRemove,
}) => {
    /** 当前选中单元格关联的循环块 */
    const currentBlock = useMemo(() => {
        if (!selectedCell) return null;
        return findBlockAtCell(loopBlocks, selectedCell.sheetId, selectedCell.row, selectedCell.column);
    }, [selectedCell, loopBlocks]);

    if (!currentBlock) {
        return (
            <div className="report-engine__property-section">
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                        <span style={{ fontSize: 12 }}>
                            当前单元格不在循环块内
                            <br />
                            <span style={{ color: '#999' }}>右键点击单元格可快速创建循环块</span>
                        </span>
                    }
                    style={{ padding: '24px 0' }}
                />
            </div>
        );
    }

    const handleChange = (field: keyof LoopBlockConfig, value: string) => {
        onLoopBlockChange(currentBlock.id, { ...currentBlock, [field]: value });
    };

    return (
        <div className="report-engine__property-section">
            <div style={{ marginBottom: 8 }}>
                <Tag color="blue">
                    第 {currentBlock.startRow + 1}~{currentBlock.endRow + 1} 行，
                    第 {currentBlock.startColumn + 1}~{currentBlock.endColumn + 1} 列
                </Tag>
            </div>

            <div style={{ marginBottom: 12 }}>
                <label style={{ fontSize: 12, color: '#666', display: 'block', marginBottom: 4 }}>循环块标签</label>
                <Input
                    size="small"
                    placeholder="如：部门循环、月份循环"
                    value={currentBlock.label || ''}
                    onChange={(e) => handleChange('label', e.target.value)}
                />
            </div>

            <Popconfirm
                title="确定移除该循环块？"
                onConfirm={() => onLoopBlockRemove(currentBlock.id)}
                okText="确定"
                cancelText="取消"
            >
                <Button size="small" danger icon={<DeleteOutlined />}>
                    移除循环块
                </Button>
            </Popconfirm>
        </div>
    );
};

export default LoopBlockSection;
