import { defineConfig } from '@rstest/core';
import { withRslibConfig } from '@rstest/adapter-rslib';

/**
 * rstest 配置：通过 adapter-rslib 继承 rslib.config.ts（alias `@/`、define、source 等）。
 * 采用 multi-project 分层（见 report-frontend/TESTING.md §3 / §10）：
 * - node：happy-dom，跑交互逻辑层 + 结构层（test 目录下 *.test.tsx，排除 *.style.test）
 * - browser：playwright chromium headless，跑样式层 computed style + 几何（test 目录下 *.style.test.tsx）
 *
 * browser 字段可在 project 级设置（ProjectConfig 的 Omit 列表不含 browser）。
 * 测试代码统一置于与 src 平级的 test/ 目录，保持 src 纯净。
 */
export default defineConfig({
  projects: [
    {
      name: 'node',
      extends: withRslibConfig(),
      testEnvironment: 'happy-dom',
      setupFiles: ['./test/setup.ts'],
      globals: true,
      include: ['test/**/*.{test,spec}.{ts,tsx}'],
      exclude: ['test/**/*.{style,visual}.{test,spec}.{ts,tsx}'],
    },
    {
      name: 'browser',
      extends: withRslibConfig(),
      browser: {
        enabled: true,
        provider: 'playwright',
        browser: 'chromium',
        headless: true,
      },
      setupFiles: ['./test/browser-setup.ts'],
      globals: true,
      include: ['test/**/*.style.{test,spec}.{ts,tsx}'],
    },
  ],
});
