package com.codingapi.report.operator.condition;

/**
 * 条件算子（SPI）：一个 {@link CompareOperator} 对应一个实现，<b>唯一职责是判断"左值 op 右值"是否成立</b>。
 *
 * <h3>设计动机：用策略替代 switch</h3>
 *
 * <p>原本比较逻辑集中在一个 {@code switch(operator)} 里——每加一个算子就要改这个方法， 且未实现的算子被 {@code default}
 * 静默放行（导致过滤失效）。改为按算子拆成独立策略后：
 *
 * <ul>
 *   <li><b>开闭原则</b>：新增算子 = 新增一个实现类并注册，不改动已有代码
 *   <li><b>显式失败</b>：没有策略支持的算子由 {@link ConditionPredicates} 抛异常，而非静默放行
 * </ul>
 *
 * 这与数据提取层的 {@code DataExtractor}（{@code supports()} + 注册列表）是同一套扩展范式。
 *
 * <h3>输入约定</h3>
 *
 * <p>右值已由 {@code ParamContext} 解析为实际值（字面量/参数/循环字段都已求值）， 所以本接口只接收两个裸值，不感知参数体系——保持纯粹、易测、无外部依赖。
 *
 * @see CompareOperator
 * @see ConditionPredicates
 */
public interface ConditionPredicate {

    /** 是否支持指定比较算子。{@link ConditionPredicates} 遍历注册表，用此方法选中实现。 */
    boolean supports(CompareOperator operator);

    /**
     * 判断 {@code left op right} 是否成立。
     *
     * @param left 条件左值（从数据行取出的字段值）
     * @param right 条件右值（已由 ParamContext 解析后的实际值）
     */
    boolean test(Object left, Object right);
}
