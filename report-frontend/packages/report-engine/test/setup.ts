import '@testing-library/jest-dom';
import { server } from './msw-server';

/**
 * antd 组件在 happy-dom 下需要的 polyfill。
 * happy-dom 自带 getComputedStyle / window；缺 matchMedia、ResizeObserver。
 */
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
}

if (!window.ResizeObserver) {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

// MSW 生命周期：默认 handler 兜成功态；单测内 server.use() 就近覆盖异态，afterEach 自动还原。
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
