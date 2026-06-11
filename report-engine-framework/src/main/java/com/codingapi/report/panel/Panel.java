package com.codingapi.report.panel;

import com.codingapi.report.components.IComponent;
import com.codingapi.report.display.Display;

import java.util.List;

/**
 * 容器面板
 */
public class Panel {

    /**
     * 高度
     */
    private int index;

    /**
     * 布局
     */
    private Display display;

    /**
     * 组件
     */
    private List<IComponent> children;
}
