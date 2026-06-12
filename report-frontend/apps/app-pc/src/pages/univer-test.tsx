/**
 * Univer 技术验证页面（使用 report-univer 组件）
 *
 * 验证目标：
 * 1. UniverSheet 组件封装（初始化、插件、locale 全由组件管理）
 * 2. 导入导出能力（loadSnapshot / getSnapshot，round-trip 验证）
 * 3. 循环块管理（高亮 + 外部 CRUD）
 * 4. CellHandle 操作句柄（样式读写、值设置）
 * 5. 单元格属性绑定（领域特定的属性模板，由应用层管理）
 */

import React, { useRef, useState, useMemo, useCallback } from 'react';
import { Button, Tag, Space, Select, Input, Empty, Divider, message as antdMessage } from 'antd';
import { DatabaseOutlined, HighlightOutlined, DeleteOutlined, PlusOutlined, PlayCircleOutlined, DownloadOutlined, UploadOutlined, ExperimentOutlined } from '@ant-design/icons';

// report-univer 组件和类型
import {
  UniverSheet,
  findBlockAtCell,
} from '@coding-report/report-univer';
import type {
  UniverSheetHandle,
  SelectedCellInfo,
  CellHandle,
  CellProp,
  CellPropStore,
  CellStyleSnapshot,
  LoopBlockConfig,
  FieldDropInfo,
  MenuGroupDef,
  CellRange,
} from '@coding-report/report-univer';
import { makeCellKey, makeMergeKey, MessageType } from '@coding-report/report-univer';

// 领域特定属性模板
import type { PropKindTemplate } from './univer-test-props';
import { PROP_KINDS, PROP_KIND_MAP } from './univer-test-props';

// 测试数据
import { MOCK_SNAPSHOT, STYLE_TEST_SNAPSHOT } from './univer-test-utils';

// API
import { exportExcel, importExcel, fetchFonts } from '@/api/example';
import { mockDataConfig } from '../data/mock-data';

// ─── 字段选项构建 ──────────────────────────────────────

const fieldOptions = mockDataConfig.tables.flatMap((table) =>
  table.fields.map((f) => ({
    label: `${table.alias || table.name}.${f.alias || f.name}`,
    value: `${table.name}.${f.name}`,
  })),
);

// ─── 辅助 ──────────────────────────────────────────────

let blockIdCounter = 0;
const genBlockId = () => `loop-${++blockIdCounter}-${Date.now().toString(36)}`;

// ─── 组件 ──────────────────────────────────────────────

const UniverTestPage: React.FC = () => {
  const sheetRef = useRef<UniverSheetHandle>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 当前选中的单元格信息
  const [selectedInfo, setSelectedInfo] = useState<SelectedCellInfo | null>(null);
  // 当前选中单元格的样式快照（回填编辑器）
  const [selectedStyle, setSelectedStyle] = useState<CellStyleSnapshot>({});
  // CellHandle 引用（用于操作当前选中单元格）
  const cellHandleRef = useRef<CellHandle | null>(null);

  // 属性存储
  const [propStore, setPropStore] = useState<CellPropStore>({
    cellProps: {},
    mergeProps: {},
    loopBlockProps: {},
  });

  // 循环块
  const [loopBlocks, setLoopBlocks] = useState<Record<string, LoopBlockConfig>>({});

  // 只读模式
  const [readOnly, setReadOnly] = useState(false);

  // 添加属性表单
  const [addingPropKind, setAddingPropKind] = useState<string | null>(null);
  const [newPropField, setNewPropField] = useState<string>('');
  const [newPropData, setNewPropData] = useState<Record<string, string>>({});

  // 消息提示
  const [message, setMessage] = useState<{ content: string; type?: MessageType } | null>(null);

  // 导出 Excel 加载状态
  const [exporting, setExporting] = useState(false);

  // 导入 Excel 加载状态
  const [importing, setImporting] = useState(false);

  // ─── 右键菜单（循环块） ──────────────────────────

  const contextMenuGroups = useMemo<MenuGroupDef[]>(() => ([
    {
      id: 'loop-block',
      title: '循环块',
      items: [
        {
          id: 'loop-block-set',
          title: '设置',
          tooltip: '将选中区域设置为循环块',
          onClick: (range: CellRange) => {
            const width = range.endColumn - range.startColumn + 1;
            const height = range.endRow - range.startRow + 1;
            if (width <= 1 && height <= 1) {
              setMessage({ content: '请先选择多个单元格再设置循环块', type: MessageType.Warning });
              return;
            }
            const block: LoopBlockConfig = {
              id: genBlockId(),
              sheetId: range.sheetId,
              startRow: range.startRow,
              startColumn: range.startColumn,
              endRow: range.endRow,
              endColumn: range.endColumn,
              label: `循环块 ${Object.keys(loopBlocks).length + 1}`,
            };
            setLoopBlocks((prev) => ({ ...prev, [block.id]: block }));
          },
        },
        {
          id: 'loop-block-edit',
          title: '编辑',
          tooltip: '编辑当前循环块配置',
          onClick: (range: CellRange) => {
            const block = findBlockAtCell(loopBlocks, range.sheetId, range.startRow, range.startColumn);
            if (block) {
              // 选中循环块内的单元格，右侧面板会显示循环块配置
              setSelectedInfo({
                sheetId: range.sheetId,
                row: range.startRow,
                column: range.startColumn,
                a1Notation: '',
                value: null,
                mergeRange: null,
              });
            }
          },
        },
        {
          id: 'loop-block-remove',
          title: '移除',
          tooltip: '移除当前循环块',
          onClick: (range: CellRange) => {
            const block = findBlockAtCell(loopBlocks, range.sheetId, range.startRow, range.startColumn);
            if (block) {
              setLoopBlocks((prev) => {
                const next = { ...prev };
                delete next[block.id];
                return next;
              });
              setPropStore((prev) => {
                const next = { ...prev.loopBlockProps };
                delete next[block.id];
                return { ...prev, loopBlockProps: next };
              });
            }
          },
        },
      ],
    },
  ]), [loopBlocks]);

  // ─── Univer 就绪后加载字体 ──────────────────────────────

  const handleReady = useCallback(async () => {
    const CACHE_KEY = 'report-fonts-v1';

    try {
      // 优先从 localStorage 读取缓存
      let items: { family: string; filename: string }[] | null = null;
      try {
        const cached = localStorage.getItem(CACHE_KEY);
        if (cached) items = JSON.parse(cached);
      } catch { /* 缓存解析失败，走 API */ }

      // 缓存未命中，从 API 获取并写入缓存
      if (!items) {
        items = await fetchFonts();
        if (items.length > 0) {
          try { localStorage.setItem(CACHE_KEY, JSON.stringify(items)); } catch { /* 忽略 */ }
        }
      }

      if (!items || items.length === 0) return;

      // .ttc（TrueType Collection）浏览器 @font-face 无法加载，跳过
      const loadable = items.filter(
        (item) => !item.filename.toLowerCase().endsWith('.ttc'),
      );
      if (loadable.length === 0) return;

      // 已注入过 @font-face 且 Univer 已注册则跳过
      if (document.getElementById('report-engine-fonts')) {
        return;
      }

      // 注入 @font-face 规则（字体文件由浏览器 HTTP 缓存提供，不重复下载）
      const styleEl = document.createElement('style');
      styleEl.id = 'report-engine-fonts';
      styleEl.textContent = loadable.map((item) => `
@font-face {
  font-family: '${item.family}';
  src: url('/api/fonts/file/${encodeURIComponent(item.filename)}');
  font-display: swap;
}`).join('\n');
      document.head.appendChild(styleEl);

      // 注册到 Univer 字体下拉菜单
      sheetRef.current?.addFonts(
        loadable.map((item) => ({ value: item.family, label: item.family })),
      );
    } catch {
      console.warn('加载后端字体列表失败，将仅使用内置字体');
    }
  }, []);

  // ─── 单元格选中回调 ──────────────────────────────────

  const handleCellSelect = useCallback((
    info: SelectedCellInfo,
    handle: CellHandle,
    _cellProps: CellProp[] | undefined,
  ) => {
    setSelectedInfo(info);
    setSelectedStyle(handle.getStyle());
    cellHandleRef.current = handle;
  }, []);

  // ─── 拖拽回调 ──────────────────────────────────────

  const handleFieldDrop = useCallback((info: FieldDropInfo, handle: CellHandle) => {
    handle.setValue(info.data);
  }, []);

  // ─── 当前选中的属性上下文 ──────────────────────────

  const currentContext = useMemo(() => {
    if (!selectedInfo) return null;

    const { sheetId, row, column, mergeRange } = selectedInfo;

    // 检查是否在循环块内
    const inLoopBlock = findBlockAtCell(loopBlocks, sheetId, row, column);

    if (mergeRange) {
      const mk = makeMergeKey(sheetId, mergeRange.startRow, mergeRange.startColumn, mergeRange.endRow, mergeRange.endColumn);
      return {
        type: 'merge' as const,
        key: mk,
        label: `合并区域 (${mergeRange.startRow},${mergeRange.startColumn}) → (${mergeRange.endRow},${mergeRange.endColumn})`,
        props: propStore.mergeProps[mk] || [],
        loopBlock: inLoopBlock || null,
      };
    }

    if (inLoopBlock) {
      return {
        type: 'loopBlock' as const,
        key: inLoopBlock.id,
        label: `循环块: ${inLoopBlock.label || inLoopBlock.id}`,
        props: propStore.loopBlockProps[inLoopBlock.id] || [],
        loopBlock: inLoopBlock,
      };
    }

    const ck = makeCellKey(sheetId, row, column);
    return {
      type: 'cell' as const,
      key: ck,
      label: selectedInfo.a1Notation,
      props: propStore.cellProps[ck] || [],
      loopBlock: null,
    };
  }, [selectedInfo, propStore, loopBlocks]);

  // ─── 属性 CRUD ──────────────────────────────────

  const updateProps = useCallback((target: 'cell' | 'merge' | 'loopBlock', key: string, updater: (prev: CellProp[]) => CellProp[]) => {
    setPropStore((prev) => {
      const storeKey = target === 'cell' ? 'cellProps' : target === 'merge' ? 'mergeProps' : 'loopBlockProps';
      const current = prev[storeKey][key] || [];
      return { ...prev, [storeKey]: { ...prev[storeKey], [key]: updater(current) } };
    });
  }, []);

  const handleAddProp = useCallback(() => {
    if (!currentContext || !addingPropKind) return;

    const template = PROP_KIND_MAP[addingPropKind];
    if (!template) return;

    const data: Record<string, unknown> = { ...template.createDefault() };
    for (const [k, v] of Object.entries(newPropData)) {
      data[k] = v;
    }

    const newProp: CellProp = { kind: addingPropKind, data };
    if (newPropField) newProp.field = newPropField;

    updateProps(currentContext.type, currentContext.key, (prev) => [...prev, newProp]);
    setAddingPropKind(null);
    setNewPropField('');
    setNewPropData({});
  }, [currentContext, addingPropKind, newPropField, newPropData, updateProps]);

  const handleRemoveProp = useCallback((index: number) => {
    if (!currentContext) return;
    updateProps(currentContext.type, currentContext.key, (prev) => prev.filter((_, i) => i !== index));
  }, [currentContext, updateProps]);

  // ─── 循环块 ──────────────────────────────────

  const handleCreateLoopBlock = useCallback(() => {
    if (!selectedInfo) return;
    const { sheetId, row, column } = selectedInfo;

    const block: LoopBlockConfig = {
      id: genBlockId(),
      sheetId,
      startRow: row,
      startColumn: column,
      endRow: row + 2,
      endColumn: column + 1,
      label: `循环块 ${Object.keys(loopBlocks).length + 1}`,
    };

    setLoopBlocks((prev) => ({ ...prev, [block.id]: block }));
  }, [selectedInfo, loopBlocks]);

  const handleClearLoopBlocks = useCallback(() => {
    setLoopBlocks({});
    setPropStore((prev) => ({ ...prev, loopBlockProps: {} }));
  }, []);

  // ─── 快照 ──────────────────────────────────

  const handleSnapshot = useCallback(() => {
    const snapshot = sheetRef.current?.getSnapshot();
    if (snapshot) {
      console.group('📊 [Excel 快照]');
      console.log(JSON.stringify(snapshot, null, 2));
      console.groupEnd();
    }
  }, []);

  // ─── 渲染快照 ──────────────────────────────────

  const handleRender = useCallback(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const result = sheetRef.current?.loadSnapshot(MOCK_SNAPSHOT as any);
    if (result) {
      setPropStore((prev) => ({
        cellProps: { ...prev.cellProps, ...result.cellProps },
        mergeProps: { ...prev.mergeProps, ...result.mergeProps },
        loopBlockProps: { ...prev.loopBlockProps, ...result.loopBlockProps },
      }));
      // 添加循环块
      const newBlocks: Record<string, LoopBlockConfig> = {};
      for (const lb of result.loopBlocks) {
        newBlocks[lb.id] = lb;
      }
      setLoopBlocks((prev) => ({ ...prev, ...newBlocks }));
    }
  }, []);

  // ─── 加载样式测试数据 ──────────────────────────

  const handleLoadStyleTest = useCallback(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const result = sheetRef.current?.loadSnapshot(STYLE_TEST_SNAPSHOT as any);
    if (result) {
      setPropStore((prev) => ({
        cellProps: { ...prev.cellProps, ...result.cellProps },
        mergeProps: { ...prev.mergeProps, ...result.mergeProps },
        loopBlockProps: { ...prev.loopBlockProps, ...result.loopBlockProps },
      }));
      antdMessage.success('样式测试数据已加载');
    }
  }, []);

  // ─── 导出 Excel ──────────────────────────────

  const handleExportExcel = useCallback(async () => {
    const snapshot = sheetRef.current?.getSnapshot();
    if (!snapshot) {
      antdMessage.warning('无法获取快照数据');
      return;
    }

    setExporting(true);
    try {
      const blob = await exportExcel(snapshot);

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'report.xlsx';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      antdMessage.success('Excel 导出成功');
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : '未知错误';
      antdMessage.error(`导出失败: ${errMsg}`);
    } finally {
      setExporting(false);
    }
  }, []);

  // ─── 导入 Excel ──────────────────────────────

  const handleImportExcel = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleFileChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // 重置 input，以便同一文件可再次选择
    e.target.value = '';

    setImporting(true);
    try {
      const workbook = await importExcel(file);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const result = sheetRef.current?.loadSnapshot(workbook as any);
      if (result) {
        setPropStore((prev) => ({
          cellProps: { ...prev.cellProps, ...result.cellProps },
          mergeProps: { ...prev.mergeProps, ...result.mergeProps },
          loopBlockProps: { ...prev.loopBlockProps, ...result.loopBlockProps },
        }));
        const newBlocks: Record<string, LoopBlockConfig> = {};
        for (const lb of result.loopBlocks) {
          newBlocks[lb.id] = lb;
        }
        setLoopBlocks((prev) => ({ ...prev, ...newBlocks }));
      }
      antdMessage.success('Excel 导入成功');
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : '未知错误';
      antdMessage.error(`导入失败: ${errMsg}`);
    } finally {
      setImporting(false);
    }
  }, []);

  // ─── 样式操作（通过 CellHandle） ──────────────────

  const handle = cellHandleRef.current;

  // ─── 渲染 ──────────────────────────────────

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* 工具栏 */}
      <div style={{
        padding: '8px 12px',
        borderBottom: '1px solid #e8e8e8',
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}>
        <Space>
          <Button type="primary" icon={<DatabaseOutlined />} onClick={handleSnapshot}>
            获取快照
          </Button>
          <Button icon={<PlayCircleOutlined />} onClick={handleRender}>
            渲染快照
          </Button>
          <Button icon={<ExperimentOutlined />} onClick={handleLoadStyleTest}>
            样式测试数据
          </Button>
          <Button
            type="primary"
            ghost
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={handleExportExcel}
          >
            导出 Excel
          </Button>
          <Button
            icon={<UploadOutlined />}
            loading={importing}
            onClick={handleImportExcel}
          >
            导入 Excel
          </Button>
          <Button icon={<HighlightOutlined />} onClick={handleCreateLoopBlock}>
            创建循环块
          </Button>
          {Object.keys(loopBlocks).length > 0 && (
            <Button icon={<DeleteOutlined />} danger onClick={handleClearLoopBlocks}>
              清除循环块 ({Object.keys(loopBlocks).length})
            </Button>
          )}
          <Button
            type={readOnly ? 'primary' : 'default'}
            danger={readOnly}
            onClick={() => setReadOnly((v) => !v)}
          >
            {readOnly ? '🔒 只读模式' : '✏️ 编辑模式'}
          </Button>
        </Space>

        {/* 可拖拽字段 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 8 }}>
          <span style={{ fontSize: 12, color: '#999' }}>拖入字段:</span>
          {mockDataConfig.tables.slice(0, 2).flatMap((table) =>
            table.fields.slice(0, 3).map((f) => {
              const fieldCode = `${table.name}.${f.name}`;
              const displayName = f.alias || f.name;
              return (
                <span
                  key={fieldCode}
                  draggable
                  onDragStart={(e) => {
                    e.dataTransfer.setData('text/plain', displayName);
                    e.dataTransfer.effectAllowed = 'copy';
                  }}
                  style={{
                    display: 'inline-block',
                    padding: '2px 8px',
                    background: '#f0f5ff',
                    border: '1px solid #adc6ff',
                    borderRadius: 4,
                    fontSize: 12,
                    cursor: 'grab',
                    userSelect: 'none',
                  }}
                >
                  {displayName}
                </span>
              );
            })
          )}
        </div>

        {selectedInfo && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <Tag color="default" style={{ margin: 0 }}>{selectedInfo.a1Notation}</Tag>
            <span style={{ color: '#666', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {selectedInfo.value !== null ? String(selectedInfo.value) : '(空)'}
            </span>
            {selectedInfo.mergeRange && <Tag color="orange" style={{ margin: 0 }}>合并单元格</Tag>}
          </div>
        )}
      </div>

      {/* 主体：表格 + 属性面板 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* UniverSheet 组件 */}
        <UniverSheet
          ref={sheetRef}
          style={{ flex: 1, height: '100%' }}
          onReady={handleReady}
          onCellSelect={handleCellSelect}
          onFieldDrop={handleFieldDrop}
          contextMenuGroups={contextMenuGroups}
          loopBlocks={loopBlocks}
          cellProps={propStore.cellProps}
          mergeProps={propStore.mergeProps}
          loopBlockProps={propStore.loopBlockProps}
          readOnly={readOnly}
          message={message}
          onMessageConsumed={() => setMessage(null)}
        />

        {/* 右侧属性面板 */}
        <div style={{
          width: 300,
          borderLeft: '1px solid #e8e8e8',
          overflow: 'auto',
          padding: 12,
          flexShrink: 0,
        }}>
          {!selectedInfo || !currentContext ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择单元格" />
          ) : (
            <>
              {/* 选中信息头 */}
              <div style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <Tag color={currentContext.type === 'merge' ? 'orange' : currentContext.type === 'loopBlock' ? 'green' : 'blue'}>
                    {currentContext.type === 'merge' ? '合并区域' : currentContext.type === 'loopBlock' ? '循环块' : '单元格'}
                  </Tag>
                  <span style={{ fontWeight: 500 }}>{currentContext.label}</span>
                </div>

                {selectedInfo.mergeRange && (
                  <div style={{
                    padding: '6px 10px',
                    background: '#fff7e6',
                    border: '1px solid #ffd591',
                    borderRadius: 6,
                    fontSize: 12,
                    marginTop: 4,
                  }}>
                    <div style={{ fontWeight: 500, marginBottom: 2 }}>合并范围</div>
                    <div style={{ color: '#666' }}>
                      起始: ({selectedInfo.mergeRange.startRow}, {selectedInfo.mergeRange.startColumn}) →
                      结束: ({selectedInfo.mergeRange.endRow}, {selectedInfo.mergeRange.endColumn})
                    </div>
                  </div>
                )}
              </div>

              {/* 循环块配置 */}
              {currentContext.loopBlock && currentContext.type !== 'loopBlock' && (
                <div style={{
                  padding: '8px 10px',
                  background: '#f6ffed',
                  border: '1px solid #b7eb8f',
                  borderRadius: 6,
                  marginBottom: 12,
                  fontSize: 12,
                }}>
                  <div style={{ fontWeight: 500, marginBottom: 4 }}>所在循环块: {currentContext.loopBlock.label}</div>
                  <div style={{ color: '#666' }}>
                    范围: ({currentContext.loopBlock.startRow},{currentContext.loopBlock.startColumn}) → ({currentContext.loopBlock.endRow},{currentContext.loopBlock.endColumn})
                  </div>
                </div>
              )}

              {/* 样式设置 */}
              <Divider style={{ margin: '8px 0' }}>样式设置</Divider>
              <div key={selectedInfo.a1Notation} style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 4 }}>
                <div style={{ display: 'flex', gap: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>字体颜色</div>
                    <input
                      type="color"
                      defaultValue={selectedStyle.fontColor || '#000000'}
                      onChange={(e) => handle?.setFontColor(e.target.value)}
                      style={{ width: '100%', height: 28, border: '1px solid #d9d9d9', borderRadius: 4, cursor: 'pointer', padding: 2 }}
                    />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>背景颜色</div>
                    <input
                      type="color"
                      defaultValue={selectedStyle.background || '#ffffff'}
                      onChange={(e) => handle?.setBackground(e.target.value)}
                      style={{ width: '100%', height: 28, border: '1px solid #d9d9d9', borderRadius: 4, cursor: 'pointer', padding: 2 }}
                    />
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>字号</div>
                    <Input
                      size="small"
                      type="number"
                      min={8}
                      max={72}
                      defaultValue={selectedStyle.fontSize}
                      placeholder="13"
                      onChange={(e) => {
                        const size = parseInt(e.target.value);
                        if (size >= 8) handle?.setFontSize(size);
                      }}
                    />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>加粗 {selectedStyle.bold ? '(当前加粗)' : ''}</div>
                    <Space size={4}>
                      <Button size="small" type={selectedStyle.bold ? 'primary' : 'default'} onClick={() => handle?.setFontWeight('bold')}>
                        <strong>B</strong>
                      </Button>
                      <Button size="small" type={!selectedStyle.bold ? 'primary' : 'default'} onClick={() => handle?.setFontWeight('normal')}>
                        取消
                      </Button>
                    </Space>
                  </div>
                </div>
                <Button size="small" danger onClick={() => handle?.clearFormat()}>
                  清除样式
                </Button>
              </div>

              <Divider style={{ margin: '8px 0' }}>属性列表 ({currentContext.props.length})</Divider>

              {currentContext.props.length === 0 ? (
                <div style={{ fontSize: 12, color: '#999', textAlign: 'center', padding: '12px 0' }}>
                  暂无属性，点击下方添加
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}>
                  {currentContext.props.map((prop, index) => (
                    <PropCard key={index} prop={prop} onRemove={() => handleRemoveProp(index)} />
                  ))}
                </div>
              )}

              <Divider style={{ margin: '8px 0' }}>添加属性</Divider>

              {addingPropKind ? (
                <PropAddForm
                  kind={addingPropKind}
                  field={newPropField}
                  data={newPropData}
                  onFieldChange={setNewPropField}
                  onDataChange={setNewPropData}
                  onConfirm={handleAddProp}
                  onCancel={() => { setAddingPropKind(null); setNewPropData({}); setNewPropField(''); }}
                />
              ) : (
                <Select
                  size="small"
                  style={{ width: '100%' }}
                  placeholder="选择属性类型"
                  options={PROP_KINDS.map((k) => ({ label: k.label, value: k.kind }))}
                  onChange={(val) => {
                    setAddingPropKind(val);
                    setNewPropData({});
                    setNewPropField('');
                  }}
                />
              )}
            </>
          )}
        </div>
      </div>

      {/* 隐藏的文件选择器 */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".xlsx"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
    </div>
  );
};

// ─── 属性卡片 ────────────────────────────────────────

const PropCard: React.FC<{ prop: CellProp; onRemove: () => void }> = ({ prop, onRemove }) => {
  const template = PROP_KIND_MAP[prop.kind];
  return (
    <div style={{
      padding: '8px 10px',
      background: '#fafafa',
      border: '1px solid #e8e8e8',
      borderRadius: 6,
      fontSize: 12,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
        <Tag color="geekblue" style={{ margin: 0 }}>{template?.label || prop.kind}</Tag>
        <Button type="text" size="small" danger onClick={onRemove}>删除</Button>
      </div>
      {prop.field && (
        <div style={{ color: '#1677ff', marginBottom: 2 }}>字段: {prop.field}</div>
      )}
      <pre style={{
        margin: 0,
        padding: '4px 6px',
        background: '#fff',
        border: '1px solid #f0f0f0',
        borderRadius: 4,
        fontSize: 11,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
        maxHeight: 80,
        overflow: 'auto',
      }}>
        {JSON.stringify(prop.data, null, 2)}
      </pre>
    </div>
  );
};

// ─── 添加属性表单 ────────────────────────────────────

const PROP_FORM_FIELDS: Record<string, Array<{ key: string; label: string; placeholder: string }>> = {
  field: [
    { key: 'display', label: '显示方式', placeholder: 'value / label / both' },
  ],
  dataConfig: [
    { key: 'pageSize', label: '每页条数', placeholder: '10' },
    { key: 'orderBy', label: '排序字段', placeholder: 'fieldName' },
    { key: 'direction', label: '排序方向', placeholder: 'asc / desc' },
  ],
  display: [
    { key: 'format', label: '格式', placeholder: 'text / number / date / currency' },
    { key: 'prefix', label: '前缀', placeholder: '' },
    { key: 'suffix', label: '后缀', placeholder: '' },
  ],
  aggregation: [
    { key: 'method', label: '聚合方式', placeholder: 'count / sum / avg / max / min' },
    { key: 'groupBy', label: '分组字段', placeholder: '' },
  ],
};

const PropAddForm: React.FC<{
  kind: string;
  field: string;
  data: Record<string, string>;
  onFieldChange: (v: string) => void;
  onDataChange: (d: Record<string, string>) => void;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ kind, field, data, onFieldChange, onDataChange, onConfirm, onCancel }) => {
  const template: PropKindTemplate | undefined = PROP_KIND_MAP[kind];
  const formFields = PROP_FORM_FIELDS[kind] || [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <Tag color="geekblue">{template?.label || kind}</Tag>

      <div>
        <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>字段（可选）</div>
        <Select
          size="small"
          style={{ width: '100%' }}
          placeholder="选择数据源字段"
          allowClear
          value={field || undefined}
          options={fieldOptions}
          onChange={(val) => onFieldChange(val || '')}
        />
      </div>

      {formFields.map((f) => (
        <div key={f.key}>
          <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>{f.label}</div>
          <Input
            size="small"
            placeholder={f.placeholder}
            value={data[f.key] || ''}
            onChange={(e) => onDataChange({ ...data, [f.key]: e.target.value })}
          />
        </div>
      ))}

      <Space size="small">
        <Button size="small" type="primary" icon={<PlusOutlined />} onClick={onConfirm}>
          确认添加
        </Button>
        <Button size="small" onClick={onCancel}>取消</Button>
      </Space>
    </div>
  );
};

export default UniverTestPage;
