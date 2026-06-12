import React, { useState, useCallback } from 'react';
import { Group } from 'react-resizable-panels';
import Header from './components/layout/header';
import Panel from './components/layout/panel';
import DataSourcePanel from './components/datasource';
import SheetPanel from './components/engine';
import LoopBlockModal from './components/engine/loop-block-modal';
import PropertyPanel from './components/properties';
import type { ReportEngineProps } from './types';
import type {
    SelectedCellInfo,
    CellPropertyMap,
    CellPropertyConfig,
    CellKey,
    LoopBlockConfig,
} from './components/properties/types';
import type { CellRange, FieldDropInfo } from '@coding-report/report-univer';
import { generateId } from './components/properties/types';
import './index.css';

export const ReportEngine: React.FC<ReportEngineProps> = ({ dataConfig, title, onSave }) => {
    // ========== 属性面板状态 ==========
    const [selectedCell, setSelectedCell] = useState<SelectedCellInfo | null>(null);
    const [cellProperties, setCellProperties] = useState<CellPropertyMap>({});

    // ========== 循环块状态 ==========
    const [loopBlocks, setLoopBlocks] = useState<Record<string, LoopBlockConfig>>({});
    const [editingLoopBlock, setEditingLoopBlock] = useState<LoopBlockConfig | null>(null);
    const [loopBlockModalOpen, setLoopBlockModalOpen] = useState(false);

    // ========== 属性面板回调 ==========
    const handleCellSelect = useCallback((info: SelectedCellInfo) => {
        setSelectedCell(info);
    }, []);

    const handleCellPropertyChange = useCallback(
        (cellKey: CellKey, config: CellPropertyConfig) => {
            setCellProperties((prev) => ({ ...prev, [cellKey]: config }));
        },
        [],
    );

    // ========== 循环块回调 ==========
    const handleCreateLoopBlock = useCallback(
        (range: CellRange) => {
            const id = generateId();
            const newBlock: LoopBlockConfig = {
                id,
                ...range,
                label: '循环块',
            };
            setLoopBlocks((prev) => ({ ...prev, [id]: newBlock }));
            // 自动打开配置弹窗
            setEditingLoopBlock(newBlock);
            setLoopBlockModalOpen(true);
        },
        [],
    );

    const handleEditLoopBlock = useCallback((id: string) => {
        setLoopBlocks((prev) => {
            const block = prev[id];
            if (block) {
                setEditingLoopBlock(block);
                setLoopBlockModalOpen(true);
            }
            return prev;
        });
    }, []);

    const handleSaveLoopBlock = useCallback((config: LoopBlockConfig) => {
        setLoopBlocks((prev) => ({ ...prev, [config.id]: config }));
        setLoopBlockModalOpen(false);
        setEditingLoopBlock(null);
    }, []);

    const handleRemoveLoopBlock = useCallback((id: string) => {
        setLoopBlocks((prev) => {
            const next = { ...prev };
            delete next[id];
            return next;
        });
        setLoopBlockModalOpen(false);
        setEditingLoopBlock(null);
    }, []);

    const handleCloseLoopBlockModal = useCallback(() => {
        setLoopBlockModalOpen(false);
        setEditingLoopBlock(null);
    }, []);

    // ========== 循环块属性面板回调 ==========
    const handleLoopBlockChange = useCallback((id: string, config: LoopBlockConfig) => {
        setLoopBlocks((prev) => ({ ...prev, [id]: config }));
    }, []);

    // ========== 字段拖入回调 ==========
    const handleFieldDrop = useCallback((info: FieldDropInfo): string | undefined => {
        try {
            const parsed = JSON.parse(info.data);
            if (parsed.table && parsed.field) {
                return `${parsed.table}.${parsed.field}`;
            }
        } catch {
            // 非 JSON 格式，直接使用原始数据
        }
        return info.data;
    }, []);

    return (
        <div className="report-engine">
            <Header
                title={title}
                onSave={onSave}
            />

            <div className="report-engine__body">
                <Group orientation="horizontal">
                    <Panel title="数据预览" position="left" defaultSize="15%" minSize="200px" collapsible>
                        {dataConfig && <DataSourcePanel dataConfig={dataConfig} />}
                    </Panel>

                    <Panel position="center" defaultSize="70%" withSeparator>
                        <SheetPanel
                            onCellSelect={handleCellSelect}
                            onCreateLoopBlock={handleCreateLoopBlock}
                            onEditLoopBlock={handleEditLoopBlock}
                            onRemoveLoopBlock={handleRemoveLoopBlock}
                            loopBlocks={loopBlocks}
                            onFieldDrop={handleFieldDrop}
                        />
                    </Panel>

                    <Panel title="属性设置" position="right" defaultSize="15%" minSize="200px" withSeparator collapsible>
                        <PropertyPanel
                            selectedCell={selectedCell}
                            dataConfig={dataConfig}
                            cellProperties={cellProperties}
                            onCellPropertyChange={handleCellPropertyChange}
                            loopBlocks={loopBlocks}
                            onLoopBlockChange={handleLoopBlockChange}
                            onLoopBlockRemove={handleRemoveLoopBlock}
                        />
                    </Panel>
                </Group>
            </div>

            {/* 循环块配置弹窗 */}
            <LoopBlockModal
                open={loopBlockModalOpen}
                config={editingLoopBlock}
                onSave={handleSaveLoopBlock}
                onRemove={handleRemoveLoopBlock}
                onClose={handleCloseLoopBlockModal}
            />
        </div>
    );
};
