package com.codingapi.report.expression.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link MapFunction} + {@link TransformRegistry}：编码→呈现映射、回退、清理。 */
class MapFunctionTest {

    private final MapFunction fn = new MapFunction();

    @AfterEach
    void tearDown() {
        TransformRegistry.clear();
    }

    @Test
    void mapsCodeToLabel() {
        TransformRegistry.set(Map.of("gender", Map.of("0", "女", "1", "男")));
        assertEquals("男", fn.apply(List.of(1, "gender")));
        assertEquals("女", fn.apply(List.of("0", "gender")));
    }

    @Test
    void mapsDoubleCodeToLabel() {
        // DB int 字段经渲染层 coerce 成 Double（0.0/1.0），需归一为 "0"/"1" 才能匹配字典编码
        TransformRegistry.set(Map.of("gender", Map.of("0", "女", "1", "男")));
        assertEquals("男", fn.apply(List.of(1.0, "gender")));
        assertEquals("女", fn.apply(List.of(0.0, "gender")));
        assertEquals("男", fn.apply(List.of(1L, "gender")));
    }

    @Test
    void fallsBackToRawWhenCodeMissing() {
        TransformRegistry.set(Map.of("gender", Map.of("0", "女")));
        assertEquals(9, fn.apply(List.of(9, "gender"))); // 编码无映射 → 原值
    }

    @Test
    void fallsBackWhenTransformMissing() {
        TransformRegistry.set(Map.of());
        assertEquals(1, fn.apply(List.of(1, "unknown"))); // 转换项不存在 → 原值
    }

    @Test
    void fallsBackWhenRegistryEmpty() {
        assertEquals(1, fn.apply(List.of(1, "gender"))); // 未装载 → 原值
    }
}
