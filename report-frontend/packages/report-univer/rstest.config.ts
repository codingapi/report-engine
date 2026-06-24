import { defineConfig } from '@rstest/core';
import { withRslibConfig } from '@rstest/adapter-rslib';

/**
 * rstest 配置：通过 adapter-rslib 继承 rslib.config.ts（alias `@/`、define、source 等）。
 * report-univer 是 Univer 底层封装，核心为纯逻辑模块（snapshot/geometry/cell-handle/highlight），
 * 无 antd / 无接口依赖，故只保留单一 node 项目（happy-dom），不引入 browser 样式层。
 * 测试代码统一置于与 src 平级的 test/ 目录，保持 src 纯净（见 report-frontend/TESTING.md §10）。
 */
export default defineConfig({
  extends: withRslibConfig(),
  testEnvironment: 'happy-dom',
  setupFiles: ['./test/setup.ts'],
  globals: true,
  include: ['test/**/*.{test,spec}.{ts,tsx}'],
});
