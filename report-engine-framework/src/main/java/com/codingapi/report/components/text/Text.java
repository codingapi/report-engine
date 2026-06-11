package com.codingapi.report.components.text;

import com.codingapi.report.components.IComponent;
import com.codingapi.report.content.IContent;
import com.codingapi.report.format.DataFormat;
import com.codingapi.report.stype.Style;
import lombok.Getter;
import lombok.Setter;

/**
 * 文字组件
 */
@Getter
@Setter
public class Text implements IComponent {

    /**
     * 样式
     */
    private Style style;

    /**
     * 文字内容
     */
    private IContent content;

    /**
     * 数据格式
     */
    private DataFormat dataFormat;

}
