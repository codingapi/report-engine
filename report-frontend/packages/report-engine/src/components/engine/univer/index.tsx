import { useEffect, useRef } from 'react';
import { UniverSheetsCorePreset } from '@univerjs/preset-sheets-core';
import UniverPresetSheetsCoreZhCN from '@univerjs/preset-sheets-core/locales/zh-CN';
import { createUniver, LocaleType, mergeLocales } from '@univerjs/presets';
import '@univerjs/preset-sheets-core/lib/index.css';
import { UniverSheetProps } from './type';

const UniverSheet: React.FC<UniverSheetProps> = (props) => {
    const containerRef = useRef<HTMLDivElement>(null);

    const style = props.style || {
        height: '100vh',
    };

    useEffect(() => {
        const { univerAPI, univer } = createUniver({
            locale: LocaleType.ZH_CN,
            locales: {
                [LocaleType.ZH_CN]: mergeLocales(
                    UniverPresetSheetsCoreZhCN,
                ),
            },
            presets: [
                UniverSheetsCorePreset({
                    container: containerRef?.current || undefined,
                    ribbonType: 'simple',
                    formulaBar: false,
                    formula: {
                        functionScreenTips: false,
                    },
                    menu: {
                        // 隐藏冻结行列功能
                        'sheet.menu.sheet-frozen': { hidden: true },
                        'sheet.column-header-menu.sheet-frozen': { hidden: true },
                        'sheet.row-header-menu.sheet-frozen': { hidden: true },
                        // 隐藏保护（权限）功能
                        'sheet.contextMenu.permission': { hidden: true },
                        'sheet.command.add-range-protection-from-toolbar': { hidden: true },
                        'sheet.command.add-range-protection-from-sheet-bar': { hidden: true },
                        'sheet.command.delete-worksheet-protection-from-sheet-bar': { hidden: true },
                        'sheet.command.change-sheet-protection-from-sheet-bar': { hidden: true },
                        'sheet.command.view-sheet-permission-from-sheet-bar': { hidden: true },
                        // 隐藏文本转换数字功能
                        'sheet.toolbar.text-to-number': { hidden: true },
                        'sheet.contextMenu.text-to-number': { hidden: true },
                        // 隐藏常用函数功能
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
                        // 隐藏数据格式功能
                        'sheet.operation.open.numfmt.panel': { hidden: true },
                        'sheet.command.numfmt.set.percent': { hidden: true },
                        'sheet.command.numfmt.set.currency': { hidden: true },
                        'sheet.command.numfmt.add.decimal.command': { hidden: true },
                        'sheet.command.numfmt.subtract.decimal.command': { hidden: true },
                    },
                }),
            ],
        })
        univerAPI.createWorkbook({})

        props.onCreate?.(univer, univerAPI, containerRef.current!);

        return () => {
            univerAPI.dispose()
        }
    }, [])
    return (
        <div ref={containerRef} style={style} />
    )
};

export default UniverSheet;
