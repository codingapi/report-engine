package com.example.report.model.source;

/**
 * 对"某数据集的某字段"的稳定引用。
 *
 * <p>所有绑定/条件/关系都用 datasetId + 字段名引用，<b>不存别名</b>——改别名不破坏绑定。
 */
public record FieldRef(String datasetId, String field) {
}
