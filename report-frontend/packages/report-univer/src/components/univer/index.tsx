import React, { useEffect, useImperativeHandle, useRef, forwardRef } from 'react';
import { setupUniver } from '../../core/setup';
import type { UniverAPI } from '../../core/setup';
import { registerCellSelection } from '../../core/cell-selection';
import { buildContextMenus, updateMenuGroups } from '../../core/context-menu';
import { createHighlightManager } from '../../core/highlight';
import type { HighlightManager } from '../../core/highlight';
import { registerDragDrop } from '../../core/drag-drop';
import { extractSnapshot } from '../../core/snapshot';
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

        const style = props.style || { height: '100vh' };

        // 暴露命令式句柄
        useImperativeHandle(ref, () => ({
            getSnapshot: () => {
                const api = univerAPIRef.current;
                if (!api) return null;
                const workbook = api.getActiveWorkbook();
                if (!workbook) return null;
                return extractSnapshot(workbook);
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
        }));

        // 初始化 Univer（仅一次）
        useEffect(() => {
            if (!containerRef.current) return;

            const { univerAPI, dispose } = setupUniver(containerRef.current);
            univerAPIRef.current = univerAPI;

            // 创建高亮管理器
            highlightManagerRef.current = createHighlightManager(univerAPI);

            // 注册单元格选中事件（含高亮重应用）
            registerCellSelection(
                univerAPI,
                () => onCellSelectRef.current,
                () => highlightManagerRef.current,
            );

            // 构建右键菜单
            if (props.contextMenuGroups?.length) {
                buildContextMenus(univerAPI, props.contextMenuGroups);
                menusBuiltRef.current = true;
            }

            // 注册拖拽事件
            const cleanupDragDrop = registerDragDrop(
                containerRef.current,
                univerAPI,
                () => onFieldDropRef.current,
            );

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
