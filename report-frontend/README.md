# Report Engine

基于 [Univer](https://univer.ai) 的 React 电子表格/报表组件库。

## 项目结构

```
report-engine/
├── packages/
│   └── report-engine/    # 组件库 (@coding-report/report-engine)
└── apps/
    └── app-pc/           # Demo 应用
```

## 快速开始

```bash
# 安装依赖
pnpm install

# 启动组件库 watch 模式
pnpm run watch:report-engine

# 启动 Demo 应用开发服务器（新终端）
pnpm run dev:app-pc

# 构建组件库
pnpm run build
```

## 当前状态

项目处于早期开发阶段，主要功能：

- [x] 三栏布局（数据源配置 / 表格 / 属性设置）
- [x] 可拖拽调整面板宽度
- [x] 左右面板折叠/展开
- [ ] 数据源配置面板
- [ ] 属性设置面板
- [ ] 更多表格功能扩展

## 技术栈

- React 18 + TypeScript 5.9
- Univer v0.24（电子表格引擎）
- antd v6（UI 组件）
- Rslib / Rsbuild（构建工具）
- pnpm workspaces

## License

Apache 2.0
