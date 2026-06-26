import { useCallback, useState } from "react";
function useDatasource(service) {
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const refresh = useCallback(async ()=>{
        if (!service?.listDataSources) return void setError(new Error('listDataSources 未注入'));
        setLoading(true);
        setError(null);
        try {
            const data = await service.listDataSources();
            setList(data);
        } catch (e) {
            setError(e);
        } finally{
            setLoading(false);
        }
    }, [
        service
    ]);
    const create = useCallback(async (config)=>{
        if (!service?.createDataSource) {
            setError(new Error('createDataSource 未注入'));
            return null;
        }
        try {
            const id = await service.createDataSource(config);
            await refresh();
            return id;
        } catch (e) {
            setError(e);
            return null;
        }
    }, [
        service,
        refresh
    ]);
    const update = useCallback(async (config)=>{
        if (!service?.updateDataSource) {
            setError(new Error('updateDataSource 未注入'));
            return false;
        }
        try {
            await service.updateDataSource(config);
            await refresh();
            return true;
        } catch (e) {
            setError(e);
            return false;
        }
    }, [
        service,
        refresh
    ]);
    const remove = useCallback(async (id)=>{
        if (!service?.deleteDataSource) {
            setError(new Error('deleteDataSource 未注入'));
            return false;
        }
        try {
            await service.deleteDataSource(id);
            await refresh();
            return true;
        } catch (e) {
            setError(e);
            return false;
        }
    }, [
        service,
        refresh
    ]);
    return {
        list,
        loading,
        error,
        refresh,
        create,
        update,
        remove
    };
}
export { useDatasource };
