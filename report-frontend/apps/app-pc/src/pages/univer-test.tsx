/**
 * Univer 技术验证页面
 *
 * 验证目标：
 * 1. 手动 plugins 方式初始化 Univer（不使用 preset），去除自带公式插件
 * 2. 获取工作表快照数据（单元格内容 + 样式 + 合并信息）
 */

import React, { useEffect, useRef, useState } from 'react';
import { Button, Tag } from 'antd';
import { DatabaseOutlined } from '@ant-design/icons';

// Univer 核心
import { createUniver, LocaleType, mergeLocales } from '@univerjs/presets';
import type { FUniver } from '@univerjs/presets';
import type { FWorksheet } from '@univerjs/core/facade';

// 基础插件
import { UniverNetworkPlugin } from '@univerjs/network';
import { UniverDocsPlugin } from '@univerjs/docs';
import { UniverRenderEnginePlugin } from '@univerjs/engine-render';
import { UniverUIPlugin } from '@univerjs/ui';
import { UniverDocsUIPlugin } from '@univerjs/docs-ui';

// 公式引擎（单元格编辑依赖其数据管道）
import { UniverFormulaEnginePlugin } from '@univerjs/engine-formula';

// 电子表格核心
import { UniverSheetsPlugin } from '@univerjs/sheets';
import { UniverSheetsUIPlugin } from '@univerjs/sheets-ui';

// 数字格式
import { UniverSheetsNumfmtPlugin } from '@univerjs/sheets-numfmt';
import { UniverSheetsNumfmtUIPlugin } from '@univerjs/sheets-numfmt-ui';

// 公式与表格交互（单元格编辑依赖，必须保留）
import { UniverSheetsFormulaPlugin } from '@univerjs/sheets-formula';
import { UniverSheetsFormulaUIPlugin } from '@univerjs/sheets-formula-ui';

// 中文 locale
import UniverUIZhCN from '@univerjs/ui/locale/zh-CN';
import UniverDocsUIZhCN from '@univerjs/docs-ui/locale/zh-CN';
import UniverSheetsZhCN from '@univerjs/sheets/locale/zh-CN';
import UniverSheetsUIZhCN from '@univerjs/sheets-ui/locale/zh-CN';
import UniverSheetsNumfmtUIZhCN from '@univerjs/sheets-numfmt-ui/locale/zh-CN';
import UniverSheetsFormulaZhCN from '@univerjs/sheets-formula/locale/zh-CN';
import UniverSheetsFormulaUIZhCN from '@univerjs/sheets-formula-ui/locale/zh-CN';

// Facade 模块增强（为 FUniver 扩展方法）
import '@univerjs/sheets/facade';
import '@univerjs/ui/facade';
import '@univerjs/sheets-ui/facade';
import '@univerjs/network/facade';
import '@univerjs/docs-ui/facade';
import '@univerjs/sheets-formula/facade';
import '@univerjs/sheets-formula-ui/facade';

// CSS（使用 preset 的合并 CSS，确保所有样式完整）
import '@univerjs/preset-sheets-core/lib/index.css';

// 数据提取工具
import { extractWorkbookSnapshot } from './univer-test-utils';

// 值类型映射
const VALUE_TYPE_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '字符串', color: 'blue' },
  1: { label: '数字', color: 'green' },
  2: { label: '布尔', color: 'orange' },
};

interface CellInfo {
  position: string;
  value: string | number | boolean | null;
  valueType: number | null;
  hasRichText: boolean;
  hasFormula: boolean;
  jsType: string;
}

const UniverTestPage: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const univerAPIRef = useRef<FUniver | null>(null);
  const [cellInfo, setCellInfo] = useState<CellInfo | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const { univerAPI } = createUniver({
      locale: LocaleType.ZH_CN,
      locales: {
        [LocaleType.ZH_CN]: mergeLocales(
          UniverUIZhCN,
          UniverDocsUIZhCN,
          UniverSheetsZhCN,
          UniverSheetsUIZhCN,
          UniverSheetsNumfmtUIZhCN,
          UniverSheetsFormulaZhCN,
          UniverSheetsFormulaUIZhCN,
        ),
      },
      presets: [], // 不使用任何 preset
      plugins: [
        [UniverNetworkPlugin],
        [UniverDocsPlugin],
        [UniverRenderEnginePlugin],
        [UniverUIPlugin, {
          container: containerRef.current,
          ribbonType: 'simple' as const,
          formulaBar: false, // 隐藏公式栏
          menu: {
            // 隐藏常用函数菜单
            'formula-ui.operation.insert-function.common': { hidden: true },
            'formula-ui.operation.insert-function.financial': { hidden: true },
            'formula-ui.operation.insert-function.logical': { hidden: true },
            'formula-ui.operation.insert-function.text': { hidden: true },
            'formula-ui.operation.insert-function.date': { hidden: true },
            'formula-ui.operation.insert-function.lookup': { hidden: true },
            'formula-ui.operation.insert-function.math': { hidden: true },
            'formula-ui.operation.insert-function.statistical': { hidden: true },
            'formula-ui.operation.insert-function.engineering': { hidden: true },
            'formula-ui.operation.insert-function.information': { hidden: true },
            'formula-ui.operation.insert-function.database': { hidden: true },
            'formula-ui.operation.more-functions': { hidden: true },
            // 隐藏文本转数字
            'sheet.toolbar.text-to-number': { hidden: true },
            'sheet.contextMenu.text-to-number': { hidden: true },
          },
        }],
        [UniverDocsUIPlugin],
        // 公式引擎：正常加载（单元格编辑依赖其数据管道）
        [UniverFormulaEnginePlugin],
        // 电子表格核心
        [UniverSheetsPlugin],
        [UniverSheetsUIPlugin, { formulaBar: false }],
        // 数字格式
        [UniverSheetsNumfmtPlugin],
        [UniverSheetsNumfmtUIPlugin],
        // 公式插件：必须保留（单元格编辑依赖其数据管道）
        // 公式功能通过隐藏菜单 UI 禁用，notExecuteFormula 仅适用于 Web Worker 模式
        [UniverSheetsFormulaPlugin],
        [UniverSheetsFormulaUIPlugin, { functionScreenTips: false }],
      ],
    });

    univerAPI.createWorkbook({});
    univerAPIRef.current = univerAPI;

    // 监听单元格选中事件，更新标题栏信息
    univerAPI.addEvent(univerAPI.Event.SelectionChanged, (params) => {
      const { worksheet: ws, selections } = params as { worksheet: FWorksheet; selections: Array<{ startRow: number; startColumn: number }> };
      if (!selections || selections.length === 0) return;

      const { startRow: row, startColumn: col } = selections[0];

      const fRange = ws.getRange(row, col);
      const value = fRange.getValue();
      const a1 = fRange.getA1Notation();

      // 读取原始单元格数据（含类型信息）
      let valueType: number | null = null;
      let hasRichText = false;
      let hasFormula = false;
      const raw = (ws as unknown as Record<string, Function>).getCellRaw?.(row, col);
      if (raw) {
        valueType = raw.t ?? null;
        hasRichText = !!raw.p;
        hasFormula = typeof raw.f === 'string' && raw.f.length > 0;
      }

      setCellInfo({
        position: a1,
        value: value as string | number | boolean | null,
        valueType,
        hasRichText,
        hasFormula,
        jsType: value === null || value === undefined ? 'null' : typeof value,
      });
    });

    console.log('✅ [Univer 验证] 手动 plugins 模式初始化完成');

    return () => {
      univerAPI.dispose();
    };
  }, []);

  const handleSnapshot = () => {
    const fWorkbook = univerAPIRef.current?.getActiveWorkbook();
    if (!fWorkbook) {
      console.warn('⚠️ 未找到活动工作簿');
      return;
    }
    extractWorkbookSnapshot(fWorkbook);
  };

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
        <Button type="primary" icon={<DatabaseOutlined />} onClick={handleSnapshot}>
          获取快照
        </Button>

        {cellInfo && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <Tag color="default" style={{ margin: 0 }}>{cellInfo.position}</Tag>

            {cellInfo.valueType !== null && (
              <Tag color={VALUE_TYPE_MAP[cellInfo.valueType]?.color || 'default'} style={{ margin: 0 }}>
                {VALUE_TYPE_MAP[cellInfo.valueType]?.label || `type:${cellInfo.valueType}`}
              </Tag>
            )}

            <Tag color="purple" style={{ margin: 0 }}>JS: {cellInfo.jsType}</Tag>

            {cellInfo.hasRichText && <Tag color="cyan" style={{ margin: 0 }}>富文本</Tag>}
            {cellInfo.hasFormula && <Tag color="red" style={{ margin: 0 }}>公式</Tag>}

            <span style={{ color: '#666', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {cellInfo.value !== null ? String(cellInfo.value) : '(空)'}
            </span>
          </div>
        )}
      </div>

      {/* Univer 电子表格容器 */}
      <div ref={containerRef} style={{ flex: 1, overflow: 'hidden' }} />
    </div>
  );
};

export default UniverTestPage;
