# Report Engine — 项目级决策原则

## 架构决策原则

1. **按业务域划分包** — 不按技术层划分，父包要名副其实
2. **扩展点统一范式** — `supports()` + 注册表，新增 = 新实现 + 注册，零改动接入
3. **Sealed Types** — 编译期穷尽，确保 switch 覆盖所有子类型
4. **值层与控制层分离** — CellBinding 模式：值是什么 vs 值怎么铺开
5. **模板层与语义层分离** — 视觉呈现与数据绑定完全分离

## 前端决策原则

1. **优先 antd 组件** — 不自造轮子
2. **import 路径规范** — 同目录 `./`，跨目录 `@/`，跨包用包名
3. **pnpm monorepo** — packages/ 下是库，apps/ 下是应用

## 代码提交纪律

1. 完成修改后不立即 commit，先跑测试
2. 测试通过 → commit → merge to dev
3. 不碰 main

## 技术栈约束

- 后端：Java 17 + Maven + Spring Boot
- 前端：pnpm monorepo + React + Univer + antd
- 测试：后端 `./mvnw test`，前端 `pnpm test`
- 格式化：`./scripts/format.sh`
