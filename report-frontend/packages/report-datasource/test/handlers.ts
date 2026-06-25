import { http, HttpResponse } from 'msw';

/**
 * 共享 MSW handler（node 端 setupServer 用）。
 * 仅放默认成功态兜底 handler，异态在单测内 `server.use(...)` 就近覆盖。
 *
 * 本包组件通过 DatasourceService 注入，不直接发请求；MSW 仅作占位兜底，
 * 待 Issue #30 接入真实 report-api 客户端后，按需补 /api/datasources/* 等端点。
 */
export const handlers = [
  http.get('/api/datamodels', () =>
    HttpResponse.json({ code: 200, success: true, msg: 'ok', data: [] }),
  ),
];
