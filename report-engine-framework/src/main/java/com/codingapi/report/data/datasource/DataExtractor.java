package com.codingapi.report.data.datasource;

import com.codingapi.report.data.dataset.Dataset;
import java.util.List;

/**
 * 数据提取器接口（SPI）：每种 {@link DataSourceType} 对应一个实现， <b>唯一职责是把数据取成规整的 {@link RawTable}</b>。
 *
 * <h3>在架构中的位置</h3>
 *
 * <pre>
 *   DataSource + Dataset → DataExtractor.extract() → RawTable
 *                                                       ↓
 *                                          Operators.filter/join/aggregate
 *                                                       ↓
 *                                              ReportRenderer 渲染
 * </pre>
 *
 * 这是"提取 / 加工"的分界线：
 *
 * <ul>
 *   <li><b>提取</b>（本接口的职责）：从物理数据源读取数据，转为统一的内存表格式。 每种连接类型（DB/CSV/API/Excel/JSON）各一个实现类
 *   <li><b>加工</b>（{@link Operators} 的范畴）：filter/join/aggregate 全在 Java 完成， 与数据源类型无关。这使得跨源计算（如 MySQL
 *       表 JOIN CSV 文件）成为可能
 * </ul>
 *
 * <h3>当前实现与扩展方向</h3>
 *
 * <p>当前最小实现：取整表，不做任何下推优化。后续可扩展：
 *
 * <ul>
 *   <li>单源本地过滤下推：如果条件只涉及本数据集的字段，可以在提取阶段就过滤，减少内存占用
 *   <li>投影下推：只 SELECT 用到的列，减少传输量
 *   <li>分页提取：大数据集分批读取
 * </ul>
 *
 * <h3>扩展新数据源类型</h3>
 *
 * <p>新增一个数据源类型只需三步：
 *
 * <ol>
 *   <li>在 {@link DataSourceType} 枚举中新增值（如 {@code MONGO}）
 *   <li>实现本接口：{@code supports()} 返回 true 当 type == MONGO，{@code extract()} 实现具体读取逻辑
 *   <li>注册到 {@link ReportRenderer} 的 extractors 列表中
 * </ol>
 *
 * @see CsvDataExtractor
 */
public interface DataExtractor {

    /**
     * 是否支持指定的数据源类型。
     *
     * <p>ReportRenderer 遍历 extractors 列表，调用此方法找到匹配的提取器。
     */
    boolean supports(DataSourceType type);

    /**
     * 从数据源提取一个数据集的全部数据，返回规整的内存表。
     *
     * @param source 物理连接配置（host/path 等）
     * @param dataset 数据集定义（表名 + 字段列表 + 类型信息）
     * @return 内存表，列名为限定名 {@code datasetId.field}，值已按 DataType 归一化
     */
    RawTable extract(DataSource source, Dataset dataset);

    // ============================================================
    // 管理能力（连接测试 + 元数据探查）
    // ============================================================
    //
    // 这些方法服务于"数据源管理"而非渲染：测试连接可达性、探查表/列元数据， 供建模界面选表、推断字段。提取器按能力实现——
    // 文件类（CSV/EXCEL）通常无需探查（表即文件、列即表头），可不实现走默认抛异常； DB/API 类应实现。
    // 用 default 方法而非另立 SPI，避免多接口装配；未实现的能力显式抛 UnsupportedOperationException，
    // 不会静默放行（与本项目算子/聚合/函数的注册表范式一致）。

    /**
     * 测试连接是否可达且凭证有效。
     *
     * <p>不落库、不影响现有数据，仅建连 + 最小探测（如 {@code SELECT 1}）后立即关闭。 默认抛 {@link
     * UnsupportedOperationException}，表示该提取器不支持连接测试（文件类无此概念）。
     */
    default TestResult test(DataSource source) {
        throw new UnsupportedOperationException("提取器不支持连接测试");
    }

    /** 探查连接下可用的表/集合列表（DB/API 类实现）。 默认抛 {@link UnsupportedOperationException}。 */
    default List<String> listTables(DataSource source) {
        throw new UnsupportedOperationException("提取器不支持表探查");
    }

    /** 探查指定表的列元数据（DB/API 类实现）。 默认抛 {@link UnsupportedOperationException}。 */
    default List<ColumnMeta> listColumns(DataSource source, String table) {
        throw new UnsupportedOperationException("提取器不支持列探查");
    }
}
