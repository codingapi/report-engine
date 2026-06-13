package com.example.report.model.grid;

import com.example.report.model.param.ValueRef;
import com.example.report.model.source.Query;
import lombok.Builder;
import lombok.Data;

/**
 * 循环块：带迭代上下文的扩展容器（典型场景：薪资条——循环动态呈现每个人的薪资）。
 *
 * <p><b>驱动不是绑一个裸数据源，而是绑一个 {@link Query}</b>：数据集 + 过滤 + 分组。
 * 迭代集合本身就是一次小查询，所以循环范围也能被参数控制（例：只循环 {@code :deptId}
 * 部门下、在职的员工），分组则决定"逐行迭代"还是"按分组去重迭代"。
 *
 * <p><b>循环导出的参数如何被识别（免登记）：</b>循环把"当前迭代行"发布进作用域，
 * 其可引用字段 = {@link #source} 驱动数据集的字段。块内格子的条件用
 * {@link ValueRef.LoopField}{@code (loopId, field)} 直接引用，无需预先登记 Parameter。
 * 属性面板枚举可选值时，沿作用域链向上收集：报表参数 ∪ 各祖先循环驱动数据集的字段。
 *
 * <p>这是"关联两个表"的第二种方式（相对静态 JOIN）：父迭代传键 → 子查询逐次执行，
 * 适合子源异构（甚至是 API），代价是 N+1 次提取（工程上需批量预取）。
 */
@Data
@Builder
public class LoopBlock {
    private String id;
    private String label;
    /** 循环区域左上角 */
    private CellRef start;
    /** 循环区域右下角 */
    private CellRef end;
    /** 驱动查询：数据集 + 过滤 + 分组，决定迭代集合 */
    private Query source;
}
