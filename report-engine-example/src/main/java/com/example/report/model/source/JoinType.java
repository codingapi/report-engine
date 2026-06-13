package com.example.report.model.source;

/**
 * JOIN 类型：两个数据集关联时的行匹配策略。
 *
 * <ul>
 *   <li>{@link #INNER}：只保留两侧都能匹配上的行（交集）。最常用，当前引擎已实现</li>
 *   <li>{@link #LEFT}：保留左侧全部行，右侧匹配不上则补 null。常用于"员工 + 可选的学历信息"</li>
 *   <li>{@link #RIGHT}：保留右侧全部行，左侧匹配不上则补 null（LEFT 的镜像）</li>
 *   <li>{@link #FULL}：两侧全部保留，匹配不上的各自补 null</li>
 * </ul>
 *
 * <p>注：当前 {@code Operators.join()} 只实现了 INNER JOIN，其余类型预留枚举值，
 * 按需扩展即可（hash join 算法本身不难扩展）。
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
