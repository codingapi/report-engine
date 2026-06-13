package com.codingapi.report.model.source;

/**
 * 字段数据类型：业务层简化的类型系统，与数据库物理类型解耦。
 *
 * <h3>为什么不用 JDBC 的 {@code java.sql.Types}？</h3>
 * <p>JDBC 类型有几十种（VARCHAR/CHAR/LONGVARCHAR/NVARCHAR...），但对于报表设计来说，
 * 用户只关心"这个字段是文本还是数字？能不能求和？能不能比较大小？"。
 * 简化为 6 种业务类型后：
 * <ul>
 *   <li>前端属性面板可以按类型智能过滤可用算子（NUMBER 才能 SUM，STRING 只能 COUNT）</li>
 *   <li>条件面板可以按类型过滤可用比较符（STRING 才有 LIKE，DATE 才有 BETWEEN）</li>
 *   <li>数据提取时统一转换为对应的 Java 类型（NUMBER→Double, BOOLEAN→Boolean, 其余→String）</li>
 * </ul>
 *
 * <h3>类型映射示例</h3>
 * <pre>
 *   PostgreSQL VARCHAR  → STRING
 *   PostgreSQL INTEGER  → NUMBER
 *   PostgreSQL DATE     → DATE
 *   PostgreSQL TIMESTAMP→ DATETIME
 *   PostgreSQL BOOLEAN  → BOOLEAN
 *   PostgreSQL JSONB    → JSON
 * </pre>
 */
public enum DataType {
    /** 文本类型，支持 LIKE/EQ 等字符串比较，可 COUNT 不可 SUM */
    STRING,
    /** 数值类型，支持 SUM/AVG/MAX/MIN 等算术聚合和大小比较 */
    NUMBER,
    /** 日期类型（无时间），支持 BETWEEN 范围比较 */
    DATE,
    /** 日期时间类型（含时间），支持 BETWEEN 范围比较和精确到秒的排序 */
    DATETIME,
    /** 布尔类型，支持 COUNT_TRUE/COUNT_FALSE 特殊聚合 */
    BOOLEAN,
    /** JSON 类型，通常作为嵌套数据透传，不支持常规聚合 */
    JSON
}
