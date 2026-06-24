import '@testing-library/jest-dom';

/**
 * 浏览器端（browser project / 真浏览器样式层）setup。
 *
 * 与 node 端 setup.ts 的差异：
 * - 真实浏览器自带 matchMedia / ResizeObserver / getComputedStyle，**无需 polyfill**；
 * - 当前样式层测试（drill-editor.style.test）无接口依赖，暂不接 MSW。
 *   后续接入有接口依赖的组件时，在此用 msw `setupWorker`（需先 `msw init`
 *   生成 worker 脚本并随 browser dev server 托管），复用 ./handlers。
 *
 * 接口走 MSW 的纪律仍适用：默认 handler 兜成功，异态用 worker.use() 就近覆盖。
 */
