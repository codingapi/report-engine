package com.codingapi.report.components.image;

import com.codingapi.report.components.IComponent;
import com.codingapi.report.content.IContent;
import com.codingapi.report.stype.Style;
import lombok.Getter;
import lombok.Setter;

/**
 * 图片组件
 */
@Getter
@Setter
public class Image implements IComponent {

    /**
     * 样式
     */
    private Style style;

    /**
     * 图片地址
     */
    private IContent url;


}
