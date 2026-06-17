import { defineConfig, js, ts } from '@rslint/core';

export default defineConfig([
  js.configs.recommended,
  ts.configs.recommended,
  {
    // 排除构建产物和生成文件
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      '**/*.d.ts',
    ],
    rules: {
      // 允许 console（开发阶段）
      'no-console': 'off',
      // 允许显式 any（Univer API 类型为 any，渐进式完善）
      '@typescript-eslint/no-explicit-any': 'off',
      // 允许空函数（接口默认实现等场景）
      '@typescript-eslint/no-empty-function': 'off',
      // Function 类型用于 Univer Facade API 的类型 workaround
      '@typescript-eslint/no-unsafe-function-type': 'off',
      // 允许未使用变量/参数以 _ 开头
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
      }],
      // 允许 require imports（CommonJS 兼容场景）
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
]);
