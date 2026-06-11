import { DataConfig } from './components/datasource/types';

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
}
