package com.codingapi.report.data.datamodel;

import java.util.List;

/**
 * 数据转换项（用户自定义字典）：把字段的原始编码映射为友好呈现文本。
 *
 * <p>配在 {@link DataModel} 下，报表配置时由 {@code map(字段, 转换项id)} 函数引用。 例如性别字段存 {@code 0/1}，配一个转换项
 * {@code [{0,女},{1,男}]}，报表展示「男/女」。
 *
 * <p>{@link TransformEntry} 支持 {@code parent} 形成树形（如多级地区/分类字典），渲染映射只按 {@code code} 平铺查找。
 *
 * @param id 唯一标识
 * @param name 标识名（英文/引用名，报表中以 map(字段, name) 引用）
 * @param alias 别名（中文名，面向用户展示）
 * @param entries 映射条目（编码 → 呈现，可带父级构成树形）
 */
public record TransformItem(String id, String name, String alias, List<TransformEntry> entries) {

    /**
     * 转换条目：一条 编码 → 呈现 的映射。
     *
     * @param code 原始编码（字段实际存储值，按字符串比较）
     * @param label 呈现文本（映射后展示）
     * @param parent 父级编码（树形结构用，顶层为 null/空）
     */
    public record TransformEntry(String code, String label, String parent) {}
}
