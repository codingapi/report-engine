import { http, HttpResponse } from 'msw';

/**
 * 共享 MSW handler（jsdom setupServer 与浏览器 setupWorker 共用一份，不维护两套 mock）。
 *
 * 约定（见 report-frontend/TESTING.md §4）：
 * - 这里只放**默认成功态**兜底 handler，保证常态请求能通；
 * - 异常态（失败/锁定/网络错误）在单测内用 `server.use(...)` 就近覆盖，`afterEach` 自动还原；
 * - 常用场景抽成命名工厂（见文件末尾示例）。
 *
 * 当前尚未有带接口依赖的组件试点，先放一个示例 handler 验证骨架可用，后续按需补充各端点。
 */
export const handlers = [
  // 示例：数据集列表成功态。后续接入有接口依赖的组件（如预览/数据集加载）时，
  // 在这里补齐 /api/report/*、/api/datasets、/api/datamodels 等常态 handler。
  http.get('/api/datasets', () =>
    HttpResponse.json({ code: 200, success: true, msg: 'ok', data: [] }),
  ),
];

/**
 * 命名工厂示例（用时 `server.use(datasetsFailed())`）：
 *
 *   export const datasetsFailed = () =>
 *     http.get('/api/datasets', () =>
 *       HttpResponse.json({ code: 500, msg: '服务异常' }, { status: 500 }),
 *     );
 *
 * 一次性（先失败后重试）：传 `{ once: true }`。
 */
