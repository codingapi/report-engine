package com.codingapi.report.data.relation;

/**
 * 关系来源：标记一条 {@link Relationship} 是怎么产生的，用于 UI 区分展示和编辑权限。
 *
 * <ul>
 *   <li>{@link #AUTO}：从数据库外键元数据自动扫描（通过 JDBC DatabaseMetaData.getImportedKeys）。
 *       <p>优点：无需用户手动配置，数据库里建了外键就自动出现在模型里。
 *       <p>限制：只能在同一个数据库内发现，无法跨源。通常 UI 上以灰色/锁定样式展示， 表示"这是数据库声明的事实"。
 *   <li>{@link #MANUAL}：用户在建模界面手动拖线连接两个字段。
 *       <p>优点：可以跨源（如 MySQL 表连 CSV 文件）、可以连接没有物理外键的逻辑关联。
 *       <p>UI 上通常可编辑/删除。
 * </ul>
 *
 * <p>区分来源的实际意义：AUTO 关系不应该被用户意外删除（它是数据库事实）， MANUAL 关系允许自由编辑。删除一个 AUTO 关系时 UI 可以弹出确认提示。
 */
public enum RelationOrigin {
    /** 自动扫描：从数据库外键元数据发现 */
    AUTO,
    /** 手动连线：用户在界面上拖线创建，可跨源 */
    MANUAL
}
