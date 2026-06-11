package com.codingapi.report.stype;

import lombok.Builder;

@Builder
public class Style {

    /**
     * 宽度
     */
    private float width;

    /**
     * 长度
     */
    private float height;

    /**
     * 字体大小
     */
    private int fontSize;

    /**
     * 字体颜色
     */
    private String fontColor;

    /**
     * 上内边距
     */
    private float paddingTop;

    /**
     * 左内边距
     */
    private float paddingLeft;

    /**
     * 右内边距
     */
    private float paddingRight;

    /**
     * 底内边距
     */
    private float paddingBottom;


    /**
     * 上外边距
     */
    private float marginTop;

    /**
     * 左外边距
     */
    private float marginLeft;

    /**
     * 右外边距
     */
    private float marginRight;

    /**
     * 下外边距
     */
    private float marginBottom;

    /**
     * 背景色
     */
    private String backgroundColor;



}
