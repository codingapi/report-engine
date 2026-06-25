import { useCallback, useEffect, useState } from "react";
function useExplore(service, sourceId) {
    const [tables, setTables] = useState([]);
    const [columns, setColumns] = useState([]);
    const [activeTable, setActiveTable] = useState(null);
    const [loadingTables, setLoadingTables] = useState(false);
    const [loadingColumns, setLoadingColumns] = useState(false);
    const [error, setError] = useState(null);
    const refreshTables = useCallback(async ()=>{
        if (!sourceId) return void setTables([]);
        if (!service?.exploreTables) {
            setError(new Error('exploreTables 未注入'));
            setTables([]);
            return;
        }
        setLoadingTables(true);
        setError(null);
        try {
            const data = await service.exploreTables(sourceId);
            setTables(data);
        } catch (e) {
            setError(e);
            setTables([]);
        } finally{
            setLoadingTables(false);
        }
    }, [
        service,
        sourceId
    ]);
    const refreshColumns = useCallback(async (table)=>{
        if (!sourceId || !table) return void setColumns([]);
        if (!service?.exploreColumns) {
            setError(new Error('exploreColumns 未注入'));
            setColumns([]);
            return;
        }
        setLoadingColumns(true);
        setError(null);
        try {
            const data = await service.exploreColumns(sourceId, table);
            setColumns(data);
        } catch (e) {
            setError(e);
            setColumns([]);
        } finally{
            setLoadingColumns(false);
        }
    }, [
        service,
        sourceId
    ]);
    const selectTable = useCallback((table)=>{
        setActiveTable(table);
        if (table) refreshColumns(table);
        else setColumns([]);
    }, [
        refreshColumns
    ]);
    useEffect(()=>{
        refreshTables();
        setActiveTable(null);
        setColumns([]);
    }, [
        refreshTables
    ]);
    return {
        tables,
        columns,
        activeTable,
        loadingTables,
        loadingColumns,
        error,
        refreshTables,
        selectTable
    };
}
export { useExplore };
