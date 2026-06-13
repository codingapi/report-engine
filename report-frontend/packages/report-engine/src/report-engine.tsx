import React, { useState, useCallback, useRef } from 'react';
import { Group } from 'react-resizable-panels';
import { Button, Drawer, Space } from 'antd';
import Header from './components/layout/header';
import Panel from './components/layout/panel';
import DataSourcePanel from './components/datasource';
import SheetPanel from './components/engine';
import LoopBlockModal from './components/engine/loop-block-modal';
import PropertyPanel from './components/properties';
import type { SheetPanelHandle } from './components/engine';
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

export const ReportEngine: React.FC<ReportEngineProps> = ({
    dataConfig,
    title,
    onSave,
    onImport,
    onExport,
    onFontRequest,
}) => {
    // ========== Sheet 句柄 ==========
    const sheetPanelRef = useRef<SheetPanelHandle>(null);

    // ========== 模式状态 ==========
    const [mode, setMode] = useState<'design' | 'preview'>('design');
    const [importing, setImporting] = useState(false);
    const [exporting, setExporting] = useState(false);

    // ========== 属性面板状态 ==========
    const [selectedCell, setSelectedCell] = useState<SelectedCellInfo | null>(null);
    const [cellProperties, setCellProperties] = useState<CellPropertyMap>({});

    // ========== 循环块状态 ==========
    const [loopBlocks, setLoopBlocks] = useState<Record<string, LoopBlockConfig>>({});
    const [editingLoopBlock, setEditingLoopBlock] = useState<LoopBlockConfig | null>(null);
    const [loopBlockModalOpen, setLoopBlockModalOpen] = useState(false);
    const [loopBlockDrawerOpen, setLoopBlockDrawerOpen] = useState(false);

    // ========== 导入导出 ==========
    const handleImportFile = useCallback(async (file: File) => {
        if (!onImport) return;
        setImporting(true);
        try {
            const snapshot = await onImport(file);
            sheetPanelRef.current?.loadSnapshot(snapshot);
        } finally {
            setImporting(false);
        }
    }, [onImport]);

    const handleExport = useCallback(async () => {
        if (!onExport) return;
        const snapshot = sheetPanelRef.current?.getSnapshot();
        if (!snapshot) return;
        setExporting(true);
        try {
            await onExport(snapshot);
        } finally {
            setExporting(false);
        }
    }, [onExport]);

    // ========== 模式切换 ==========
    const handleModeToggle = useCallback(() => {
        setMode((prev) => (prev === 'design' ? 'preview' : 'design'));
    }, []);

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
    const handleCreateLoopBlock = useCallback((range: CellRange) => {
        const id = generateId();
        const newBlock: LoopBlockConfig = { id, ...range, label: '循环块' };
        setLoopBlocks((prev) => ({ ...prev, [id]: newBlock }));
        setEditingLoopBlock(newBlock);
        setLoopBlockModalOpen(true);
    }, []);

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

    const handleLoopBlockChange = useCallback((id: string, config: LoopBlockConfig) => {
        setLoopBlocks((prev) => ({ ...prev, [id]: config }));
    }, []);

    // ========== 字段拖入回调 ==========
    const handleFieldDrop = useCallback((info: FieldDropInfo): string | undefined => {
        try {
            const parsed = JSON.parse(info.data);
            if (parsed.table && parsed.field) return `${parsed.table}.${parsed.field}`;
        } catch {
            // 非 JSON 格式，直接使用原始数据
        }
        return info.data;
    }, []);

    // ========== SheetPanel（设计/预览共用）==========
    const sheetPanel = (
        <SheetPanel
            ref={sheetPanelRef}
            onCellSelect={mode === 'design' ? handleCellSelect : undefined}
            onCreateLoopBlock={mode === 'design' ? handleCreateLoopBlock : undefined}
            onEditLoopBlock={mode === 'design' ? handleEditLoopBlock : undefined}
            onRemoveLoopBlock={mode === 'design' ? handleRemoveLoopBlock : undefined}
            loopBlocks={loopBlocks}
            onFieldDrop={mode === 'design' ? handleFieldDrop : undefined}
            onFontRequest={onFontRequest}
        />
    );

    return (
        <div className="report-engine">
            <Header
                title={title}
                onSave={onSave}
                onImport={onImport ? handleImportFile : undefined}
                onExport={onExport ? handleExport : undefined}
                mode={mode}
                onModeToggle={handleModeToggle}
                loopBlockCount={Object.keys(loopBlocks).length}
                onManageLoopBlocks={() => setLoopBlockDrawerOpen(true)}
                importing={importing}
                exporting={exporting}
            />

            <div className="report-engine__body">
                {mode === 'preview' ? (
                    <div style={{ height: '100%' }}>{sheetPanel}</div>
                ) : (
                    <Group orientation="horizontal">
                        <Panel title="数据预览" position="left" defaultSize="15%" minSize="200px" collapsible>
                            {dataConfig && <DataSourcePanel dataConfig={dataConfig} />}
                        </Panel>

                        <Panel position="center" defaultSize="70%" withSeparator>
                            {sheetPanel}
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
                )}
            </div>

            {/* 循环块编辑弹窗 */}
            <LoopBlockModal
                open={loopBlockModalOpen}
                config={editingLoopBlock}
                onSave={handleSaveLoopBlock}
                onRemove={handleRemoveLoopBlock}
                onClose={handleCloseLoopBlockModal}
            />

            {/* 循环块管理抽屉 */}
            <Drawer
                title="循环块管理"
                open={loopBlockDrawerOpen}
                onClose={() => setLoopBlockDrawerOpen(false)}
                width={320}
            >
                {Object.keys(loopBlocks).length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '32px 0', color: '#999' }}>
                        <p>暂无循环块</p>
                        <p style={{ fontSize: 12 }}>在表格中选中多个单元格后右键 → 循环块 → 设置</p>
                    </div>
                ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                        {Object.values(loopBlocks).map((block) => (
                            <div
                                key={block.id}
                                style={{
                                    padding: '8px 12px',
                                    border: '1px solid #e8e8e8',
                                    borderRadius: 6,
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                }}
                            >
                                <div>
                                    <div style={{ fontWeight: 600 }}>{block.label || '未命名循环块'}</div>
                                    <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>
                                        行 {block.startRow + 1}–{block.endRow + 1}，列 {block.startColumn + 1}–{block.endColumn + 1}
                                    </div>
                                </div>
                                <Space>
                                    <Button
                                        size="small"
                                        type="link"
                                        onClick={() => {
                                            setLoopBlockDrawerOpen(false);
                                            handleEditLoopBlock(block.id);
                                        }}
                                    >
                                        编辑
                                    </Button>
                                    <Button
                                        size="small"
                                        type="link"
                                        danger
                                        onClick={() => handleRemoveLoopBlock(block.id)}
                                    >
                                        删除
                                    </Button>
                                </Space>
                            </div>
                        ))}
                    </div>
                )}
            </Drawer>
        </div>
    );
};
