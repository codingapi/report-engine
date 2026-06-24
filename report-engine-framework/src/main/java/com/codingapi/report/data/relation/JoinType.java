package com.codingapi.report.data.relation;

/**
 * JOIN 类型：两个数据集关联时的行匹配策略。{@code Operators.join()} 已实现全部四种类型。
 *
 * <ul>
 *   <li>{@link #INNER}：只保留两侧都能匹配上的行（交集）</li>
 *   <li>{@link #LEFT}：保留左侧全部行，右侧匹配不上则补 null。常用于"员工 + 可选的学历信息"</li>
 *   <li>{@link #RIGHT}：保留右侧全部行，左侧匹配不上则补 null（LEFT 的镜像）</li>
 *   <li>{@link #FULL}：两侧全部保留，匹配不上的各自补 null</li>
 * </ul>
 *
 * <p><b>方向约定</b>：LEFT/RIGHT 的"保留侧"相对 {@code Operators.join(left, right)} 的<b>参数位置</b>
 * （LEFT 保留 {@code left} 参数，RIGHT 保留 {@code right} 参数），而非本枚举字段名所暗示的
 * "关系左/右端点"。调用方始终以累积表为 {@code left} 参数，方向由此固定。
 */
public enum JoinType {
    /** 内连接：只保留双侧匹配的行 */
    INNER,
    /** 左外连接：保留左侧全部行 */
    LEFT,
    /** 右外连接：保留右侧全部行 */
    RIGHT,
    /** 全外连接：保留双侧全部行 */
    FULL
}
