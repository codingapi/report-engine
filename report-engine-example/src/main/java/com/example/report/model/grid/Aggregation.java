package com.example.report.model.grid;

/** 聚合方式。{@link #NONE} 表示取原值（明细），其余为分组聚合。 */
public enum Aggregation {
    NONE, COUNT, COUNT_DISTINCT, SUM, AVG, MAX, MIN
}
