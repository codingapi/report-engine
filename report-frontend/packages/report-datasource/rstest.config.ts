import { defineConfig } from '@rstest/core';
import { withRslibConfig } from '@rstest/adapter-rslib';

/**
 * rstest 配置：通过 adapter-rslib 继承 rslib.config.ts（alias `@/`、define、source 等）。
 * 仅 node project（happy-dom）：本包测试聚焦交互逻辑与结构层，无 computed-style 样式层测试。
 * 测试代码置于与 src 平级的 test/ 目录，保持 src 纯净。
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
    },
  ],
});
