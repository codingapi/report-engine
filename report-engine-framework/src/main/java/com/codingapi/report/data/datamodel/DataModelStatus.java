package com.codingapi.report.data.datamodel;

/**
 * 数据模型状态。
 *
 * <ul>
 *   <li>{@link #DRAFT} —— 草稿，编辑中，未发布
 *   <li>{@link #PUBLISHED} —— 已发布，可被报表引用
 * </ul>
 */
public enum DataModelStatus {
    DRAFT,
    PUBLISHED
}
