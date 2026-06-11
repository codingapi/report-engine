package com.codingapi.report.components.layout;

import com.codingapi.report.components.IComponent;
import com.codingapi.report.display.Display;
import com.codingapi.report.stype.Style;
import lombok.Getter;
import lombok.Setter;

/**
 *  布局对象
 */
@Getter
@Setter
public class Layout implements IComponent {

    /**
     * 样式
     */
    private Style style;
    /**
     * 布局
     */
    private Display display;

}
