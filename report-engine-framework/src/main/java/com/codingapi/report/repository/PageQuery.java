package com.codingapi.report.repository;

/**
 * 分页查询参数（framework 纯类型，不依赖 Spring）。
 *
 * <p>归一逻辑下沉到访问器：{@code current<1 → 1}，{@code pageSize<=0 → 10}， 使用方无需各自重复防御。
 */
public record PageQuery(int current, int pageSize) {

    /** 当前页（从 1 起，小于 1 归一为 1）。 */
    @Override
    public int current() {
        return current < 1 ? 1 : current;
    }

    /** 每页大小（小于等于 0 归一为 10）。 */
    @Override
    public int pageSize() {
        return pageSize > 0 ? pageSize : 10;
    }
}
