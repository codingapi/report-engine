/// <reference types="@rstest/core/globals" />

/**
 * 测试全局类型入口：在本文件统一引用 @rstest/core/globals（describe/it/test/expect/
 * beforeAll/afterEach/afterAll/rs/rstest 等全局 API 的类型声明）。
 *
 * tsconfig.json 的 include 已含 test/，故此文件会被加载，所有测试文件无需在每个文件
 * 顶部重复 `/// <reference types="@rstest/core/globals" />`。
 */
export {};
