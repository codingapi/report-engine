import { FUniver, Univer } from "@univerjs/presets";

// Facade 类型扩展（通过模块增强为 FUniver 添加方法）
import '@univerjs/sheets/facade';
import '@univerjs/ui/facade';
import '@univerjs/sheets-ui/facade';
import '@univerjs/network/facade';
import '@univerjs/docs-ui/facade';

export interface UniverSheetProps {
    style?: React.CSSProperties;
    onCreate?: (univer: Univer, univerAPI: FUniver, container: HTMLDivElement) => void;
}