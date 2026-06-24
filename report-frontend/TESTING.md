# 前端测试方案（Claude Code 测试纪律）

本文件既是技术方案说明，也是 Claude Code 在本仓库做前端测试时**必须遵守的纪律**。前端测试一律参照本文件执行。

---

## 1. 目标与约束

要实现的效果：对前端做**独立、可重复、全自动**的测试验证，由 Claude Code 编排整个流程。

硬性约束（不可违反）：

- 不依赖后端真实接口 —— 接口全部由 **MSW** 在网络层模拟。
- 不为测试在业务代码里加 `data-id` 等测试专用属性 —— 元素用**可访问性语义**定位（role / label / text）。
- 不为测试侵入业务代码 —— MSW 和 Testing Library 都从「外部」工作，源码零改动。
- 视觉验证**默认不使用像素截图**，改用**结构化数据对比**（纯文本，token 友好、确定性强）。
- 一切「对 / 错」判定权归**确定性的断言和基准**，不归 LLM 的主观看图。

---

## 2. 技术选型

| 用途 | 工具 |
|---|---|
| 测试运行器 | **rstest**（rsbuild 生态，Jest 兼容 API，复用现有 rsbuild/rspack 配置） |
| 组件渲染 + 查询 | **@testing-library/react**（按 role/label/text 查询，反对 test-id） |
| 用户交互模拟 | **@testing-library/user-event** |
| 接口模拟 | **MSW**（Node 端 `setupServer`；浏览器端 `setupWorker`） |
| DOM 环境 | **happy-dom**（逻辑/结构层，仓库已装、快、零环境依赖） |
| 真浏览器（仅样式层用） | rstest Browser Mode / Playwright，**headless**，只读结构化渲染数据 |

> 注：rstest Browser Mode 目前为 experimental，落地时确认当前版本对所需 API 的支持情况。

> **本项目实际选型**：DOM 环境用 **happy-dom**（仓库已装、比 jsdom 快），而非上表的 jsdom——属对方案的务实偏离，记录在案。浏览器端 `setupWorker` 留到 visual 样式层项目启用时再建，jsdom 阶段只用 `setupServer`。

---

## 3. 测试分层（核心思路）

按成本从低到高分四层，绝大多数验证落在前两层：

| 层 | 跑在哪 | 验证什么 | 失败输出 |
|---|---|---|---|
| 交互逻辑层（主力） | jsdom | 输入/点击后状态与渲染是否正确 | RTL 断言文本 |
| 结构层 | jsdom | DOM 结构、ARIA 可访问性树（语义/层级） | 文本 diff |
| 样式层 | 真浏览器 headless | 关键元素的 computed style + 几何（尺寸/坐标/遮挡） | 文本/数字 diff |
| 像素层（默认禁用） | 真浏览器 headless | 像素级视觉回归 | PNG diff |

**视觉验证用结构化对比，不用截图。** 原则是：不问「整体看起来对不对」，而是**显式声明在乎哪些属性**（背景色、文字颜色、高度、是否被遮挡、相对位置等），把它们读成 JSON 做断言或文本快照（「视觉指纹」）。

像素截图层默认关闭。仅当确有「像素级保真」刚需时才启用，且**必须由人把关**，不进 Claude Code 的高频循环。

---

## 4. MSW：同接口多场景

机制：**默认 handler 兜住常态（成功），单条测试内用 `server.use()` 临时覆盖出异态，`afterEach` 自动还原。**

setup 关键：

```ts
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers()); // 必须：清掉 .use() 临时覆盖，防止测试间污染
afterAll(() => server.close());
```

四种写法按需选用：

1. **就近覆盖**：在用例里 `server.use(http.post('/api/login', () => HttpResponse.json(...)))`，模拟成功/失败/锁定等不同响应。
2. **按请求体动态分流**：在一个 handler 里读 `request.json()`，密码对就成功、错就 401，适合放默认 handler。
3. **网络级失败**：`HttpResponse.error()` 模拟请求本身挂掉（区别于业务 401）。
4. **一次性**：`{ once: true }` 只拦第一次，用于测「先失败后重试成功」。

**约定**：把常用场景抽成命名工厂（`loginSuccess()` / `loginFailed()` / `loginLocked()`），用时 `server.use(loginFailed())`。一份 handler 在 jsdom（`setupServer`）和浏览器（`setupWorker`）两端共享，不维护两套 mock 数据。

---

## 5. Claude Code 的角色

**编排者，不是判官。**

Claude Code 该做：读组件 → 写 MSW handler、写 rstest+RTL 交互用例、写结构化视觉用例 → 跑 `pnpm test` → 读结果 → 失败时读**文本 diff** 判断是组件 bug 还是用例错 → 改代码重跑直到绿。

Claude Code **不该做**：自己开浏览器肉眼看截图判断「这看起来对不对」。那是非确定性的主观判断，不可重复，违背本方案目标。

一句话：**验证权归测试代码与基准，编排权归 Claude Code。**

---

## 6. Claude Code 必须遵守的纪律

- [ ] 元素一律用 `getByRole` / `getByLabelText` / `getByText` 等语义查询，**禁止**为测试在组件里加 `data-id`、`data-testid`。
- [ ] 接口一律走 MSW；**禁止**在业务代码里写任何测试专用的 mock 分支或环境判断。
- [ ] setup 里**必须**有 `afterEach(() => server.resetHandlers())`。
- [ ] 异常态用 `server.use()` 就近覆盖在用例内部，默认 handler 保持成功态。
- [ ] 视觉验证用**结构化对比（computed style / 几何 / ARIA / DOM 快照）**；**禁止**默认生成像素截图。
- [ ] 视觉/快照测试失败时：**只允许上报 diff 并说明判断（intended change 还是 regression），禁止擅自运行 `--update` 更新基准**。基准更新由人确认。
- [ ] 失败排查读**文本 diff**，不读图片，以控制 token。

---

## 7. monorepo 与配置要点

- rstest 支持**多项目（multi-project）**，适配 pnpm monorepo：一次运行同时跑 `unit`（jsdom）和 `visual`（browser）两个项目，各带自己的环境与 MSW 接入方式。
- 用 `@rstest/adapter-rslib` 的 `withRslibConfig()`（经 `extends` 字段接入）复用现有 rslib 配置（alias、transform），不另维护测试构建配置。注：rsbuild 项目对应 `@rstest/adapter-rsbuild`，按构建器选择对应 adapter。
- antd 注意点：`Select`/`DatePicker`/`Modal`/`Dropdown` 等渲染到 portal，用 `screen.*` 查询（查整个 document）而非 `container.*`；happy-dom 需 polyfill `matchMedia`、`ResizeObserver`（在 setup 里一次性配好）。

---

## 8. 环境一致性

- **结构化对比（本方案默认）**：基本不受 OS/字体渲染差异影响，本地与 CI 结果一致，无需特殊处理。
- **若启用像素层**：必须把视觉测试跑在固定的 **Playwright Docker 容器**里（本地、CI、Claude Code 都用同一容器），基准图也在容器内生成，否则跨环境抗锯齿差异会导致大量假失败。

---

## 9. 建议起步路径

1. 先选**一个有接口依赖 + 有成功/失败分支的组件**（如登录页）作为试点。
2. 搭 rstest 多项目骨架 + 共享 MSW handler 的两个 setup（`setupServer` / `setupWorker`）。
3. 写齐三类用例：RTL 交互用例（成功/失败/网络错误）、ARIA 或 DOM 结构快照、computed style + 几何的「视觉指纹」。
4. 跑通后再把模式复制到其他包/组件。

---

## 10. rstest 实测要点（rstest 0.9 + happy-dom + antd 6，已验证）

首个试点：`packages/report-engine/test/components/property-panel/drill-editor.test.tsx`。以下是落地中确认的、与 vitest/jest 直觉不同的点，供后续组件测试直接套用。

> 测试代码统一置于与 `src` 平级的 `test/` 目录，保持 `src` 纯净（rslib build 入口是 `src/**`，测试在 src 外可避免被误当源码入口）。

**rstest 配置是扁平结构，不是 vitest 的 `test.*` 嵌套**：

```ts
// packages/report-engine/rstest.config.ts
import { defineConfig } from '@rstest/core';
import { withRslibConfig } from '@rstest/adapter-rslib';

export default defineConfig({
  extends: withRslibConfig(),   // 继承 rslib.config.ts 的 alias(@/) / define / source
  testEnvironment: 'happy-dom', // 顶层！非 test.environment
  setupFiles: ['./test/setup.ts'], // 顶层！非 test.setupFiles
  globals: true,
});
```

**全局 API 与 vitest 不同**：rstest 没有 `vi`。`describe/it/test/expect/beforeAll/afterEach` 等开启 `globals: true` 后注入全局；mock 函数用全局 **`rs.fn()`**（或 `rstest.fn()`），`rs.spyOn()` 取代 `vi.spyOn()`。全局类型集中在 `test/globals.d.ts` 统一引用 `@rstest/core/globals`，各测试文件无需逐个加 `/// <reference>`。

**build 与 test 的 tsconfig 必须分离**（踩坑）：不要把 `test/` 加进主 `tsconfig.json` 的 `include`——rslib 生成 dts 时会把 test 文件纳入 TS program，test 的 devDep（@testing-library 等）在 build 上下文解析失败，导致 `Failed to generate declaration files`，`dist/index.d.ts` 缺失，依赖该包的下游（如 app-pc）IDE 全报 `Cannot find module`。正确做法：主 `tsconfig.json` 只 `include: ["src"]`（build/dts 用，rslib 默认读它）；另建 `test/tsconfig.json`（`extends "../tsconfig.json"` + `include: ["../src", "./**/*"]`）给测试/IDE 用——VS Code 对 `test/` 下文件会就近取 `test/tsconfig.json`，`@/`（paths 继承）与全局类型都能解析。`@/` 运行时仍靠 rslib `resolve.alias`（rstest 经 `extends: withRslibConfig()` 继承）。

**setup 必须**：`import '@testing-library/jest-dom'`（注册 `toBeInTheDocument`/`toBeChecked` 等 matcher）+ `matchMedia` / `ResizeObserver` polyfill（happy-dom 缺，antd 组件依赖）+ MSW 生命周期（`server.listen` / `afterEach(resetHandlers)` / `server.close`）。

**antd Select 的 placeholder 不在 `<input>` 上**：渲染为独立 `<div class="ant-select-placeholder">` 文本节点，`getByPlaceholderText` 查不到，改用 `getByText`。下拉选项在 portal，需点击打开后 `findByText` 取选项。

> 说明：试点组件 `DrillEditor` 纯 props 驱动、无接口依赖，故本批 MSW 仅搭骨架（`test/handlers.ts` + `msw-server.ts` + setup 接线），当前用例未触发请求；待接入有接口依赖的组件时再补常态 handler 与 `server.use()` 异态覆盖。

### 多项目分层（node 交互/结构层 ↔ browser 样式层）

`browser` 字段可在 **project 级** 设置（`ProjectConfig` 的 Omit 列表不含 `browser`），所以用 `projects` 多项目分离：node 跑 happy-dom 的交互/结构测试，browser 跑真浏览器样式测试，互不污染。`rstest.config.ts`：

```ts
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
      browser: { enabled: true, provider: 'playwright', browser: 'chromium', headless: true },
      setupFiles: ['./test/browser-setup.ts'],
      globals: true,
      include: ['test/**/*.style.{test,spec}.{ts,tsx}'],
    },
  ],
});
```

**样式层（真浏览器 chromium headless）实测要点**：

- **依赖**：`@rstest/browser@<与 @rstest/core 同版本>` + `@playwright/test` + `pnpm exec playwright install chromium`（下载约 171MiB chrome + 93MiB headless shell）。browser 项目启动报 `Browser mode requires @rstest/browser` 就是缺这个包。
- **browser-setup.ts**：真实浏览器自带 `matchMedia` / `ResizeObserver` / `getComputedStyle`，**无需 polyfill**；当前样式测试无接口依赖，暂不接 msw `setupWorker`（接入有接口依赖的浏览器测试时再 `msw init` 生成 worker 脚本并随 dev server 托管，复用 `handlers`）。
- **样式指纹不截图**：用 `window.getComputedStyle(el)` 读显式属性（`marginTop`/`display`…）、`el.getBoundingClientRect()` 读几何（`width`/`height`/`top`…）做数值断言；试点见 `drill-editor.style.test.tsx`。
- **antd CSS**：antd 6 用 css-in-js（运行时注入），样式在浏览器自动生效；组件内联 `style={{marginTop:8}}` 也能被 `getComputedStyle` 读到。
- **CLI**：`pnpm test` 跑全部项目；`rstest --project node` / `rstest --project browser` 单独跑某层（根目录已配 `pnpm test:node` / `test:browser` 脚本）。

---

## 附：交互层用例参考写法

```ts
import { http, HttpResponse } from 'msw';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from './setup';

test('成功后展示结果', async () => {
  server.use(
    http.post('/api/login', () =>
      HttpResponse.json({ token: 'abc', name: 'Alice' }),
    ),
  );
  const user = userEvent.setup();
  // render(<LoginPage />);
  await user.type(screen.getByLabelText('用户名'), 'alice');
  await user.type(screen.getByLabelText('密码'), 'secret');
  await user.click(screen.getByRole('button', { name: '登录' }));
  expect(await screen.findByText(/欢迎.*Alice/)).toBeInTheDocument();
});

test('失败时展示错误并停留在原处', async () => {
  server.use(
    http.post('/api/login', () =>
      HttpResponse.json({ message: '账号或密码错误' }, { status: 401 }),
    ),
  );
  const user = userEvent.setup();
  // render(<LoginPage />);
  await user.type(screen.getByLabelText('用户名'), 'alice');
  await user.type(screen.getByLabelText('密码'), 'wrong');
  await user.click(screen.getByRole('button', { name: '登录' }));
  expect(await screen.findByText('账号或密码错误')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
});
```
