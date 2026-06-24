package com.codingapi.report.config.dto;

import lombok.Data;

/**
 * 报表参数定义（持久化契约），对齐前端 {@code ReportParam}。
 *
 * <p>扁平结构（name/alias/dataType/defaultValue），不引入 framework {@code Parameter} 的 {@code ParamSource}
 * sealed （后者无 Jackson 注解，不便于持久化）。dataType 用 String 存储枚举名。
 */
@Data
public class ReportParam {

    private String id;

    /** 表达式中引用的名字（${name}） */
    private String name;

    /** 别名/中文名称（可选） */
    private String alias;

    /** 数据类型枚举名（STRING/NUMBER/DATE/DATETIME/BOOLEAN/JSON） */
    private String dataType;

    /** 默认值（渲染时若外部未传值则用它；无默认值则导出/预览时必须传入） */
    private String defaultValue;
}
