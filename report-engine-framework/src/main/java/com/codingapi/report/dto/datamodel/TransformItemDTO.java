package com.codingapi.report.dto.datamodel;

import java.util.List;

/**
 * 转换项出入站契约。对应领域 {@link com.codingapi.report.data.datamodel.TransformItem}。
 *
 * @param id 唯一标识
 * @param name 标识名（引用名）
 * @param alias 别名（中文名）
 * @param entries 映射条目（code/label/parent）
 */
public record TransformItemDTO(String id, String name, String alias, List<EntryDTO> entries) {

    /** 转换条目契约。 */
    public record EntryDTO(String code, String label, String parent) {}
}
