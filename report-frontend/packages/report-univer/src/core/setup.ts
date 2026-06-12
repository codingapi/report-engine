/**
 * Univer 初始化模块
 * 手动注册 14 个插件（参照 univer-test 验证过的方式），所有 @univerjs/* import 集中于此
 */

import { createUniver, LocaleType, mergeLocales } from '@univerjs/presets';

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

// CSS
import '@univerjs/preset-sheets-core/lib/index.css';

/** Univer 字体列表（仅保留通用字体，中文字体待后端字体服务就绪后动态注入） */
const FONT_LIST = [
    { value: 'Arial', label: 'Arial', category: 'sans-serif' as const },
    { value: 'Times New Roman', label: 'Times New Roman', category: 'serif' as const },
    { value: 'Tahoma', label: 'Tahoma', category: 'sans-serif' as const },
    { value: 'Verdana', label: 'Verdana', category: 'sans-serif' as const },
];

/** 需要隐藏的菜单项 */
const HIDDEN_MENUS: Record<string, { hidden: boolean }> = {
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
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type UniverAPI = any;

export interface SetupResult {
    univerAPI: UniverAPI;
    dispose: () => void;
}

/**
 * 初始化 Univer 实例（手动插件模式）
 */
export function setupUniver(container: HTMLDivElement): SetupResult {
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
        presets: [],
        plugins: [
            UniverNetworkPlugin,
            UniverDocsPlugin,
            UniverRenderEnginePlugin,
            [UniverUIPlugin, {
                container,
                ribbonType: 'simple' as const,
                formulaBar: false,
                menu: HIDDEN_MENUS,
                customFontFamily: { override: true, list: FONT_LIST },
            }],
            UniverDocsUIPlugin,
            UniverFormulaEnginePlugin,
            UniverSheetsPlugin,
            [UniverSheetsUIPlugin, { formulaBar: false }],
            UniverSheetsNumfmtPlugin,
            UniverSheetsNumfmtUIPlugin,
            UniverSheetsFormulaPlugin,
            [UniverSheetsFormulaUIPlugin, { functionScreenTips: false }],
        ],
    });

    univerAPI.createWorkbook({});

    return {
        univerAPI,
        dispose: () => univerAPI.dispose(),
    };
}
