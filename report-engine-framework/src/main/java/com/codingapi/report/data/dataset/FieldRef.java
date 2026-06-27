package com.codingapi.report.data.dataset;

/**
 * 对"某数据集的某字段"的稳定引用，是整个模型体系中使用最广泛的引用类型。
 *
 * <h3>谁在用它？</h3>
 *
 * <ul>
 *   <li>{@link com.codingapi.report.expression.Value.FieldValue} — 格子绑定的字段
 *   <li>{@link com.codingapi.report.operator.condition.Condition} — 过滤条件的左值
 *   <li>{@link com.codingapi.report.core.grid.SummaryRow} — 小计/总计的分组键
 *   <li>{@link com.codingapi.report.core.grid.SummaryCell} — 汇总行的聚合字段
 *   <li>{@link com.codingapi.report.data.relation.Relationship} — 跨数据集关联的左右端点
 * </ul>
 *
 * <h3>为什么是 {@code datasetId + field} 而不是其他方案？</h3>
 *
 * <p><b>方案一：存 Dataset 对象引用</b> → 序列化复杂，JSON 里出现嵌套对象，循环引用风险。
 *
 * <p><b>方案二：只存字段名</b> → 多数据集 join 后字段名可能冲突（两个表都有 {@code name}），无法区分。
 *
 * <p><b>方案三：存字段 UUID</b> → 可读性差，调试困难。
 *
 * <p>当前方案（datasetId + 字段名）兼顾了唯一性、可读性和序列化简洁性。
 *
 * <h3>为什么不存别名？</h3>
 *
 * <p>别名（{@link com.codingapi.report.data.dataset.Field#getAlias()}）是面向用户的展示名， 可能随时修改。FieldRef
 * 只引用物理字段名（{@code name}），改别名不破坏任何绑定、条件或关系。
 */
public record FieldRef(String datasetId, String field) {

    /**
     * 限定列名：{@code datasetId.field}，用于在 {@code RawTable} 的行 Map 中定位值。
     *
     * <p>例：{@code FieldRef("employees", "name")} → {@code "employees.name"}。 多数据集 join
     * 后用限定名避免同名字段冲突。
     */
    public String qualified() {
        return datasetId + "." + field;
    }
}
