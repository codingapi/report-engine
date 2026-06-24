import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/**
 * Node 端 MSW 实例（jsdom / 交互层测试用）。
 * 浏览器端（visual 样式层）启用后另建 setupWorker，复用同一份 handlers。
 */
export const server = setupServer(...handlers);
