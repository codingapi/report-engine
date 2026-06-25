import { describe, it, expect, vi, beforeEach } from '@rstest/core';
import http from '../src/http';
import {
  listDataModelBriefs,
  getDataModel,
  createDataModel,
  updateDataModel,
  deleteDataModel,
  testDataSource,
  exploreTables,
  exploreColumns,
} from '../src/datasource';

vi.mock('../src/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockedHttp = vi.mocked(http);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('datasource api', () => {
  it('listDataModelBriefs 请求 /datamodels 并返回 list', async () => {
    mockedHttp.get.mockResolvedValueOnce({
      data: { total: 1, list: [{ id: 'dm1', name: '默认模型' }] },
    } as never);
    const result = await listDataModelBriefs();
    expect(mockedHttp.get).toHaveBeenCalledWith('/datamodels');
    expect(result).toEqual([{ id: 'dm1', name: '默认模型' }]);
  });

  it('getDataModel 请求 /datamodels/{id} 并返回 data', async () => {
    const info = { datasets: [], relationships: [] };
    mockedHttp.get.mockResolvedValueOnce({ data: info } as never);
    const result = await getDataModel('dm1');
    expect(mockedHttp.get).toHaveBeenCalledWith('/datamodels/dm1');
    expect(result).toEqual(info);
  });

  it('createDataModel POST /datamodels 并返回 id', async () => {
    const payload = { datasets: [], relationships: [] };
    mockedHttp.post.mockResolvedValueOnce({ data: 'dm-new' } as never);
    const id = await createDataModel(payload);
    expect(mockedHttp.post).toHaveBeenCalledWith('/datamodels', payload);
    expect(id).toBe('dm-new');
  });

  it('updateDataModel PUT /datamodels/{id} 并返回 id', async () => {
    const payload = { datasets: [], relationships: [] };
    mockedHttp.put.mockResolvedValueOnce({ data: 'dm1' } as never);
    const id = await updateDataModel('dm1', payload);
    expect(mockedHttp.put).toHaveBeenCalledWith('/datamodels/dm1', payload);
    expect(id).toBe('dm1');
  });

  it('deleteDataModel DELETE /datamodels/{id}', async () => {
    mockedHttp.delete.mockResolvedValueOnce({ data: { success: true } } as never);
    await deleteDataModel('dm1');
    expect(mockedHttp.delete).toHaveBeenCalledWith('/datamodels/dm1');
  });

  it('testDataSource POST /datasources/test', async () => {
    const req = { sourceId: 'ds1' };
    const result = { ok: true, message: 'ok', latencyMs: 12 };
    mockedHttp.post.mockResolvedValueOnce({ data: result } as never);
    const r = await testDataSource(req);
    expect(mockedHttp.post).toHaveBeenCalledWith('/datasources/test', req);
    expect(r).toEqual(result);
  });

  it('exploreTables 传 sourceId 参数', async () => {
    mockedHttp.get.mockResolvedValueOnce({
      data: { total: 2, list: ['t1', 't2'] },
    } as never);
    const r = await exploreTables('ds1');
    expect(mockedHttp.get).toHaveBeenCalledWith('/datasources/tables', {
      params: { sourceId: 'ds1' },
    });
    expect(r).toEqual(['t1', 't2']);
  });

  it('exploreColumns 传 sourceId 与 table 参数', async () => {
    const cols = [
      { name: 'id', type: 'BIGINT', primaryKey: true },
      { name: 'name', type: 'VARCHAR', primaryKey: false },
    ];
    mockedHttp.get.mockResolvedValueOnce({ data: { total: 2, list: cols } } as never);
    const r = await exploreColumns('ds1', 'users');
    expect(mockedHttp.get).toHaveBeenCalledWith('/datasources/columns', {
      params: { sourceId: 'ds1', table: 'users' },
    });
    expect(r).toEqual(cols);
  });
});
