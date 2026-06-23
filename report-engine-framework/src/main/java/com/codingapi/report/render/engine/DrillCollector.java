package com.codingapi.report.render.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 反查信息收集器：渲染期间旁路捕获聚合/汇总格的贡献明细行。
 * <p>
 * 仅当显式传入渲染器时启用（null 时零影响）。捕获信息供后续 drill 端点使用，
 * 不污染预览返回的 Workbook 结构。
 * </p>
 *
 * <h3>捕获时机</h3>
 * <p>渲染器在落格前，若该格 drillEnabled = true，则调用 {@link #record(int, int, String, List)}
 * 记录该格的贡献行（已过滤的组合数据）。</p>
 *
 * <h3>数据格式</h3>
 * <p>贡献行为 RawTable 的行格式（Map&lt;String, Object&gt;，key 为限定名 datasetId.field）。</p>
 */
public class DrillCollector {

    /** 坐标 key → 反查信息 */
    private final Map<String, DrillInfo> drills = new HashMap<>();

    /**
     * 记录某格的反查信息。
     *
     * @param row       输出行号
     * @param col       输出列号
     * @param drillView 反查视图（数据集 id）
     * @param rows      贡献给该格的明细行（RawTable 行格式）
     */
    public void record(int row, int col, String drillView, List<Map<String, Object>> rows) {
        drills.put(key(row, col), new DrillInfo(drillView, rows));
    }

    /** 获取某格的反查信息（null 表示未记录） */
    public DrillInfo get(int row, int col) {
        return drills.get(key(row, col));
    }

    /** 获取所有已记录的反查信息 */
    public Map<String, DrillInfo> getAll() {
        return drills;
    }

    private static String key(int row, int col) {
        return row + ":" + col;
    }

    /**
     * 单格反查信息。
     *
     * @param drillView 反查视图（数据集 id）
     * @param rows      贡献给该格的明细行
     */
    public static class DrillInfo {
        private final String drillView;
        private final List<Map<String, Object>> rows;

        public DrillInfo(String drillView, List<Map<String, Object>> rows) {
            this.drillView = drillView;
            this.rows = rows;
        }

        public String getDrillView() {
            return drillView;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }
    }
}
