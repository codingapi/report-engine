package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 四边边框配置，对应前端 ExcelBorders 快照结构。
 * <p>
 * 每条边可独立设置线型和颜色，为 null 表示该侧不设置边框。
 * </p>
 */
@Data
public class Borders {

    /** 上边框 */
    private Border top;

    /** 右边框 */
    private Border right;

    /** 下边框 */
    private Border bottom;

    /** 左边框 */
    private Border left;
}
