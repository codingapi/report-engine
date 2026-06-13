import React, { useState, useCallback, useRef } from 'react';
import { Group } from 'react-resizable-panels';
import Header from './components/layout/header';
import Panel from './components/layout/panel';
import DataSourcePanel from './components/datasource';
import SheetPanel from './components/engine';
import LoopBlockDrawer from './components/engine/loop-block-drawer';
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
    const [loopBlockDrawerOpen, setLoopBlockDrawerOpen] = useState(false);
    const [activeLoopBlockId, setActiveLoopBlockId] = useState<string | undefined>(undefined);

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
        setActiveLoopBlockId(id);
        setLoopBlockDrawerOpen(true);
    }, []);

    const handleEditLoopBlock = useCallback((id: string) => {
        setActiveLoopBlockId(id);
        setLoopBlockDrawerOpen(true);
    }, []);

    const handleLoopBlockLabelChange = useCallback((id: string, label: string) => {
        setLoopBlocks((prev) => {
            const block = prev[id];
            if (!block) return prev;
            return { ...prev, [id]: { ...block, label } };
        });
    }, []);

    const handleRemoveLoopBlock = useCallback((id: string) => {
        setLoopBlocks((prev) => {
            const next = { ...prev };
            delete next[id];
            // 切换到剩余的第一个 tab，无则关闭抽屉
            const remaining = Object.keys(next);
            setActiveLoopBlockId(remaining[0]);
            if (remaining.length === 0) setLoopBlockDrawerOpen(false);
            return next;
        });
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

    // ========== SheetPanel ==========
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

    const loopBlockCount = Object.keys(loopBlocks).length;

    return (
        <div className="report-engine">
            <Header
                title={title}
                onSave={onSave}
                onImport={onImport ? handleImportFile : undefined}
                onExport={onExport ? handleExport : undefined}
                mode={mode}
                onModeToggle={handleModeToggle}
                loopBlockCount={loopBlockCount}
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
                            />
                        </Panel>
                    </Group>
                )}
            </div>

            <LoopBlockDrawer
                open={loopBlockDrawerOpen}
                onClose={() => setLoopBlockDrawerOpen(false)}
                loopBlocks={loopBlocks}
                activeId={activeLoopBlockId}
                onActiveChange={setActiveLoopBlockId}
                onLabelChange={handleLoopBlockLabelChange}
                onRemove={handleRemoveLoopBlock}
            />
        </div>
    );
};
