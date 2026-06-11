import React, { useEffect, useImperativeHandle, useRef, forwardRef } from 'react';
import { setupUniver } from '../../core/setup';
import type { UniverAPI } from '../../core/setup';
import { registerCellSelection } from '../../core/cell-selection';
import { buildContextMenus, updateMenuGroups } from '../../core/context-menu';
import { syncLoopBlockBorders } from '../../core/borders';
import { extractSnapshot } from '../../core/snapshot';
import type { UniverSheetProps, UniverSheetHandle } from './type';
import type { LoopBlockConfig, MenuGroupDef } from '../../types';

export const UniverSheet = forwardRef<UniverSheetHandle, UniverSheetProps>(
    (props, ref) => {
        const containerRef = useRef<HTMLDivElement>(null);
        const univerAPIRef = useRef<UniverAPI>(null);
        const prevLoopBlocksRef = useRef<Record<string, LoopBlockConfig>>({});
        const menusBuiltRef = useRef(false);

        // 用 ref 保存最新回调，避免闭包过期
        const onCellSelectRef = useRef(props.onCellSelect);
        onCellSelectRef.current = props.onCellSelect;

        const style = props.style || { height: '100vh' };

        // 暴露命令式句柄（getSnapshot）
        useImperativeHandle(ref, () => ({
            getSnapshot: () => {
                const api = univerAPIRef.current;
                if (!api) return null;
                const workbook = api.getActiveWorkbook();
                if (!workbook) return null;
                return extractSnapshot(workbook);
            },
        }));

        // 初始化 Univer（仅一次）
        useEffect(() => {
            if (!containerRef.current) return;

            const { univerAPI, dispose } = setupUniver(containerRef.current);
            univerAPIRef.current = univerAPI;

            // 注册单元格选中事件
            registerCellSelection(univerAPI, () => onCellSelectRef.current);

            // 构建右键菜单
            if (props.contextMenuGroups?.length) {
                buildContextMenus(univerAPI, props.contextMenuGroups);
                menusBuiltRef.current = true;
            }

            return dispose;
        }, []);

        // 同步菜单定义（当 props 变化时更新模块级引用）
        useEffect(() => {
            if (props.contextMenuGroups) {
                updateMenuGroups(props.contextMenuGroups);

                // 首次如果菜单还没构建，则构建
                if (!menusBuiltRef.current && props.contextMenuGroups.length && univerAPIRef.current) {
                    buildContextMenus(univerAPIRef.current, props.contextMenuGroups);
                    menusBuiltRef.current = true;
                }
            }
        }, [props.contextMenuGroups]);

        // 同步循环块边框
        useEffect(() => {
            const api = univerAPIRef.current;
            if (!api) return;

            const nextBlocks = props.loopBlocks || {};
            syncLoopBlockBorders(api, prevLoopBlocksRef.current, nextBlocks);
            prevLoopBlocksRef.current = nextBlocks;
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
