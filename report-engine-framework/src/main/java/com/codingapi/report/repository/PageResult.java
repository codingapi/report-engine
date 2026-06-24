package com.codingapi.report.repository;

import java.util.List;

/**
 * 分页结果（framework 纯类型，不依赖 Spring）。
 *
 * <p>{@code content} 紧凑构造时归一为不可变副本，{@code null} 归一为空列表。
 */
public record PageResult<T>(List<T> content, long total) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
