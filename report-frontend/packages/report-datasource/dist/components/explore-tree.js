import { jsx } from "react/jsx-runtime";
import { Empty, Spin, Tree } from "antd";
import { useMemo } from "react";
import { useExplore } from "../hooks/use-explore.js";
function ExploreTree({ sourceId, service, onSelectTable, onSelectColumn, defaultExpandedTables }) {
    const { tables, columns, activeTable, loadingTables, loadingColumns, error, selectTable } = useExplore(service, sourceId);
    const treeData = useMemo(()=>tables.map((t)=>{
            const isActive = activeTable === t.name;
            return {
                key: `table:${t.name}`,
                title: t.comment ? `${t.comment} (${t.name})` : t.name,
                isLeaf: false,
                children: isActive ? columns.map((c)=>({
                        key: `column:${t.name}:${c.name}`,
                        title: c.comment ?? c.name,
                        isLeaf: true
                    })) : void 0
            };
        }), [
        tables,
        columns,
        activeTable
    ]);
    const handleExpand = (keys)=>{
        const last = keys.length ? keys[keys.length - 1] : null;
        if ('string' == typeof last && last.startsWith('table:')) {
            const tableName = last.slice(6);
            selectTable(tableName);
            const table = tables.find((t)=>t.name === tableName) ?? null;
            onSelectTable?.(table);
        } else {
            selectTable(null);
            onSelectTable?.(null);
        }
    };
    const handleSelect = (keys)=>{
        const key = keys[0];
        if ('string' != typeof key) {
            onSelectTable?.(null);
            onSelectColumn?.(null);
            return;
        }
        if (key.startsWith('column:')) {
            const [, tableName, colName] = key.split(':');
            const col = columns.find((c)=>c.name === colName) ?? null;
            onSelectTable?.(tables.find((t)=>t.name === tableName) ?? null);
            onSelectColumn?.(col);
        } else if (key.startsWith('table:')) {
            const tableName = key.slice(6);
            const table = tables.find((t)=>t.name === tableName) ?? null;
            onSelectTable?.(table);
            onSelectColumn?.(null);
        }
    };
    if (!sourceId) return /*#__PURE__*/ jsx(Empty, {
        description: "请先选择数据源"
    });
    if (loadingTables) return /*#__PURE__*/ jsx(Spin, {});
    if (error) return /*#__PURE__*/ jsx(Empty, {
        description: error.message
    });
    if (!tables.length) return /*#__PURE__*/ jsx(Empty, {
        description: "无可用表"
    });
    const expandedKeys = defaultExpandedTables?.map((t)=>`table:${t}`) ?? (activeTable ? [
        `table:${activeTable}`
    ] : []);
    return /*#__PURE__*/ jsx(Spin, {
        spinning: loadingColumns,
        children: /*#__PURE__*/ jsx(Tree, {
            treeData: treeData,
            onExpand: handleExpand,
            onSelect: handleSelect,
            expandedKeys: expandedKeys,
            showLine: true
        })
    });
}
export default ExploreTree;
