import React, { useEffect, useImperativeHandle, useRef, forwardRef } from 'react';
import { setupUniver } from '@/core/setup';
import type { UniverAPI } from '@/core/setup';
import { registerCellSelection } from '@/core/cell-selection';
import { buildContextMenus, updateMenuGroups } from '@/core/context-menu';
import { createHighlightManager } from '@/core/highlight';
import type { HighlightManager } from '@/core/highlight';
import { registerDragDrop } from '@/core/drag-drop';
import { extractSnapshot } from '@/core/snapshot';
import { renderSnapshot } from '@/core/render';
import type { UniverSheetProps, UniverSheetHandle } from './type';

export const UniverSheet = forwardRef<UniverSheetHandle, UniverSheetProps>(
    (props, ref) => {
        const containerRef = useRef<HTMLDivElement>(null);
        const univerAPIRef = useRef<UniverAPI>(null);
        const highlightManagerRef = useRef<HighlightManager | null>(null);
        const menusBuiltRef = useRef(false);

        // 用 ref 保存最新回调，避免闭包过期
        const onCellSelectRef = useRef(props.onCellSelect);
        onCellSelectRef.current = props.onCellSelect;

        const onFieldDropRef = useRef(props.onFieldDrop);
        onFieldDropRef.current = props.onFieldDrop;

        // 用 ref 保存最新的属性存储（供 cell-selection 回调查找）
        const cellPropsRef = useRef(props.cellProps);
        cellPropsRef.current = props.cellProps;

        // 保存当前组件 props 引用（供 getSnapshot 使用）
        const propsRef = useRef(props);
        propsRef.current = props;

        const style = props.style || { height: '100vh' };

        // 暴露命令式句柄
        useImperativeHandle(ref, () => ({
            getSnapshot: () => {
                const api = univerAPIRef.current;
                if (!api) return null;
                const workbook = api.getActiveWorkbook();
                if (!workbook) return null;
                const p = propsRef.current;
                return extractSnapshot(workbook, {
                    cellProps: p.cellProps,
                    mergeProps: p.mergeProps,
                    loopBlocks: p.loopBlocks ? Object.values(p.loopBlocks) : undefined,
                    loopBlockProps: p.loopBlockProps,
                });
            },

            loadSnapshot: (snapshot) => {
                const api = univerAPIRef.current;
                if (!api) return null;
                return renderSnapshot(api, snapshot);
            },

            setCellValue: (sheetId: string, row: number, column: number, value: string) => {
                const api = univerAPIRef.current;
                if (!api) return;
                const workbook = api.getActiveWorkbook();
                if (!workbook) return;
                const sheet = workbook.getSheetBySheetId(sheetId);
                if (!sheet) return;
                sheet.getRange(row, column).setValue(value);
            },

            setSheetName: (sheetId: string, name: string) => {
                const api = univerAPIRef.current;
                if (!api) return;
                const workbook = api.getActiveWorkbook();
                if (!workbook) return;
                const sheet = workbook.getSheetBySheetId(sheetId);
                if (sheet) sheet.setName(name);
            },

            setSheetSize: (sheetId: string, rowCount: number, columnCount: number) => {
                const api = univerAPIRef.current;
                if (!api) return;
                const workbook = api.getActiveWorkbook();
                if (!workbook) return;
                const sheet = workbook.getSheetBySheetId(sheetId);
                if (!sheet) return;
                sheet.setRowCount(rowCount);
                sheet.setColumnCount(columnCount);
            },

            addFonts: (fonts) => {
                const api = univerAPIRef.current;
                if (!api || fonts.length === 0) return;
                api.addFonts(fonts);
            },
        }));

        // 初始化 Univer（仅一次）
        useEffect(() => {
            if (!containerRef.current) return;

            const { univerAPI, dispose } = setupUniver(containerRef.current);
            univerAPIRef.current = univerAPI;

            // 创建高亮管理器
            highlightManagerRef.current = createHighlightManager(univerAPI);

            // 注册单元格选中事件（含 CellHandle + cellProps）
            registerCellSelection(
                univerAPI,
                () => onCellSelectRef.current,
                () => highlightManagerRef.current,
                () => cellPropsRef.current,
            );

            // 构建右键菜单
            if (props.contextMenuGroups?.length) {
                buildContextMenus(univerAPI, props.contextMenuGroups);
                menusBuiltRef.current = true;
            }

            // 注册拖拽事件（含 CellHandle）
            const cleanupDragDrop = registerDragDrop(
                containerRef.current,
                univerAPI,
                () => onFieldDropRef.current,
            );

            // 通知父组件 Univer 已就绪
            props.onReady?.();

            return () => {
                cleanupDragDrop();
                highlightManagerRef.current?.dispose();
                highlightManagerRef.current = null;
                dispose();
            };
        }, []);

        // 同步菜单定义
        useEffect(() => {
            if (props.contextMenuGroups) {
                updateMenuGroups(props.contextMenuGroups);

                if (!menusBuiltRef.current && props.contextMenuGroups.length && univerAPIRef.current) {
                    buildContextMenus(univerAPIRef.current, props.contextMenuGroups);
                    menusBuiltRef.current = true;
                }
            }
        }, [props.contextMenuGroups]);

        // 同步循环块高亮
        useEffect(() => {
            if (!highlightManagerRef.current) return;
            highlightManagerRef.current.sync(props.loopBlocks || {});
        }, [props.loopBlocks]);

        // 同步只读模式
        useEffect(() => {
            const api = univerAPIRef.current;
            if (!api) return;
            const workbook = api.getActiveWorkbook();
            if (!workbook) return;
            workbook.setEditable(!props.readOnly);
        }, [props.readOnly]);

        // 显示消息提示
        useEffect(() => {
            if (!props.message || !univerAPIRef.current) return;

            univerAPIRef.current.showMessage({
                content: props.message.content,
                type: props.message.type || 'info',
                duration: props.message.duration ?? 2000,
            });
            props.onMessageConsumed?.();
        }, [props.message]);

        return <div ref={containerRef} style={style} />;
    },
);

UniverSheet.displayName = 'UniverSheet';
