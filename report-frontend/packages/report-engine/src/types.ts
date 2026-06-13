import { DataConfig } from './components/datasource/types';
import type { ExcelWorkbook, FontItem } from '@coding-report/report-univer';

export { DataType } from './components/datasource/types';
export type { DataConfig, TableConfig, FieldConfig, ForeignKey } from './components/datasource/types';

export { CompareOperator, CalcMethod } from './components/properties/types';
export type {
    CellPropertyConfig,
    CellPropertyMap,
    SelectedCellInfo,
    ConditionRule,
    LoopBlockConfig,
} from './components/properties/types';

export interface ReportEngineProps {
    /** 数据源配置 */
    dataConfig?: DataConfig;
    /** 报表名称 */
    title?: string;
    /** 保存回调 */
    onSave?: () => void | Promise<void>;
    /** 导入 Excel：接收文件，返回解析后的工作簿快照 */
    onImport?: (file: File) => Promise<ExcelWorkbook>;
    /** 导出 Excel：接收工作簿快照，由调用方负责生成文件和下载 */
    onExport?: (workbook: ExcelWorkbook) => Promise<void>;
    /** 字体加载：返回可用字体列表，框架自动注入 @font-face */
    onFontRequest?: () => Promise<FontItem[]>;
}
