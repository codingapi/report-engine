package com.example.report.model;

import com.example.report.model.source.DataSource;
import com.example.report.model.source.Dataset;
import com.example.report.model.source.Relationship;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据模型（可复用的语义层）：描述"数据长什么样、表之间怎么关联"，<b>与具体报表无关</b>。
 *
 * <p>把它从报表里独立出来，是为了解决"换一张报表就要重新维护一遍关系"的痛点：
 * 数据模型<b>建一次，多个 {@link Report} 引用</b>，连接/数据集/关系都不必重复维护
 * （Power BI 的 Model、Tableau 的 Data Source、帆软的服务器数据集都是这么分的）。
 *
 * <pre>
 *   DataModel（建一次，复用）            Report A ─┐
 *   ├── datasources                     Report B ─┼─ 都引用同一个 DataModel
 *   ├── datasets                        Report C ─┘
 *   └── relationships
 * </pre>
 *
 * <p>注：数据计算全部在 Java 完成，连接只负责"提取规整表"，因此这里的关系
 * （{@link Relationship}）是喂给 Java 内存 join 算子的 JoinSpec，而非 SQL JOIN。
 */
@Data
@Builder
public class DataModel {
    private String id;
    private String name;

    /** 连接（库/API/Excel，只负责提取） */
    private List<DataSource> datasources;
    /** 从连接里选出的单表/单查询，报表绑定的粒度 */
    private List<Dataset> datasets;
    /** 跨数据集关联（可跨源、可自动扫描可手动连），所有引用本模型的报表共享 */
    private List<Relationship> relationships;
}
