package com.codingapi.report.data.datamodel;

import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.relation.Relationship;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据模型（可复用的语义层）：描述"数据长什么样、表之间怎么关联"，<b>与具体报表无关</b>。
 *
 * <h3>为什么要把数据模型从报表里独立出来？</h3>
 * <p>如果不分离，每张报表都要各自维护一份"连哪些库、用哪些表、表间怎么关联"——当 10 张报表
 * 都用同一批表和关系时，改一张表名或加一条外键就要改 10 处。把连接/数据集/关系上提到
 * DataModel 后，<b>建一次，多个 {@link Report} 引用</b>，维护点收敛到一处。
 *
 * <p>这个分层是业界共识：
 * <ul>
 *   <li>Power BI 的 <i>Tabular Model</i>——连接 + 表 + 关系，多个报表共用</li>
 *   <li>Tableau 的 <i>Data Source</i>——独立于 Worksheet 发布和引用</li>
 *   <li>帆软的 <i>服务器数据集</i>——全局定义、报表引用</li>
 * </ul>
 *
 * <h3>三层结构一览</h3>
 * <pre>
 *   DataModel（建一次，复用）            Report A ─┐
 *   ├── datasources (连接)              Report B ─┼─ 都引用同一个 DataModel
 *   ├── datasets    (表/查询)           Report C ─┘
 *   └── relationships (跨表关联)
 * </pre>
 *
 * <h3>计算在哪发生？</h3>
 * <p>数据计算全部在 Java 内存完成，连接（{@link DataSource}）只负责"提取规整表"
 * （{@code RawTable}）。因此这里的 {@link Relationship} 是喂给 Java 内存 join 算子
 * 的 JoinSpec，而非下推到 SQL 的 JOIN——这样才能支持跨源（如 MySQL 表 JOIN CSV 文件）
 * 的关联。
 *
 * <h3>为什么不直接嵌在 Report 里？</h3>
 * <p>技术上可以把 datasources/datasets/relationships 全部放进 Report，但代价是：
 * <ul>
 *   <li>数据定义随报表扩散，同一张表在 N 个报表里有 N 份副本</li>
 *   <li>改字段类型/加关系需要逐报表修改，容易漏改导致不一致</li>
 *   <li>无法做"数据模型级别"的权限管理和版本控制</li>
 * </ul>
 * 独立出来后，DataModel 可以有自己的生命周期（创建、审核、发布），报表只引用 id。
 */
@Data
@Builder
public class DataModel {
    private String id;
    private String name;

    /**
     * 连接列表（库/API/Excel/CSV/JSON）。
     * <p>每个 DataSource 封装一个物理连接的配置（host、端口、凭证等），
     * 报表模板不直接引用连接，而是通过 {@link Dataset} 间接使用。
     */
    private List<DataSource> datasources;

    /**
     * 数据集列表：从连接中选出的单表/单查询，是报表绑定的最小粒度。
     * <p>一个 DataSource 下可以有多个 Dataset（比如一个库里选了 5 张表就建 5 个 Dataset），
     * 也可以是 UNION 派生数据集（多个 Dataset 纵向合并为一个统一视图）。
     */
    private List<Dataset> datasets;

    /**
     * 跨数据集关联关系，所有引用本模型的报表共享。
     * <p>可以来自数据库外键自动扫描（{@code RelationOrigin.AUTO}），
     * 也可以来自用户在界面上手动连线（{@code RelationOrigin.MANUAL}，支持跨源）。
     * <p>如果某条关系只被一张报表需要，可以放在 {@link Report#getExtraRelationships()} 里，
     * 不污染共享模型。
     */
    private List<Relationship> relationships;
}
