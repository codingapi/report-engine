/**
 * 字段拖拽处理
 * 监听容器 dragover/drop 事件，提取目标单元格信息并回调
 */

import type { FieldDropInfo } from '@/types';
import type { UniverAPI } from './setup';

/**
 * 注册拖拽事件监听
 * @param container Univer 容器 div
 * @param univerAPI Univer API（用于获取当前活动单元格）
 * @param getCallback 获取最新回调的 getter（避免闭包过期）
 * @returns 清理函数
 */
export function registerDragDrop(
    container: HTMLDivElement,
    univerAPI: UniverAPI,
    getCallback: () => ((info: FieldDropInfo) => void) | undefined,
): () => void {
    const handleDragOver = (e: DragEvent) => {
        e.preventDefault();
        if (e.dataTransfer) {
            e.dataTransfer.dropEffect = 'copy';
        }
    };

    const handleDrop = (e: DragEvent) => {
        e.preventDefault();
        const callback = getCallback();
        if (!callback) return;

        const data = e.dataTransfer?.getData('text/plain');
        if (!data) return;

        // 通过活动工作表获取目标单元格
        const workbook = univerAPI.getActiveWorkbook();
        if (!workbook) return;

        const sheet = workbook.getActiveSheet();
        if (!sheet) return;

        const activeRange = sheet.getActiveRange();
        if (!activeRange) return;

        const sheetId = sheet.getSheetId?.() as string;
        const row = activeRange.getRow() as number;
        const column = activeRange.getColumn() as number;

        callback({ sheetId, row, column, data });
    };

    container.addEventListener('dragover', handleDragOver);
    container.addEventListener('drop', handleDrop);

    return () => {
        container.removeEventListener('dragover', handleDragOver);
        container.removeEventListener('drop', handleDrop);
    };
}
