package com.codingapi.report.starter;

import com.codingapi.report.excel.FontRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 字体列表 API，供前端获取可用字体并注册到 Univer。
 * <p>
 * 仅当 classpath 中存在 Spring Web（RestController）时自动注册。
 * </p>
 */
@RestController
@RequestMapping("/api/fonts")
@ConditionalOnClass(RestController.class)
public class FontController {

    private final FontRegistry fontRegistry;

    public FontController(FontRegistry fontRegistry) {
        this.fontRegistry = fontRegistry;
    }

    /**
     * 返回当前可用的字体列表（已排序）。
     * <p>
     * 前端通过此接口获取字体后，调用 univerAPI.addFonts() 注册到 Univer。
     * 仅返回 regular 样式的字体（bold/italic 变体不单独列出）。
     * </p>
     */
    @GetMapping("/list")
    public List<FontItem> listFonts() {
        // 仅返回自定义字体（内置字体 Univer 已自带，重复添加会抛异常）
        return fontRegistry.getCustomFontCatalog().stream()
                .filter(f -> "regular".equals(f.getStyle()))
                .map(f -> new FontItem(f.getFamily(), f.getFilename()))
                .collect(Collectors.toList());
    }

    /**
     * 字体列表响应项。
     *
     * @param family   字体族名（用于 CSS font-family 和 Univer value）
     * @param filename 字体文件名（供前端按需加载字体文件时使用）
     */
    public record FontItem(String family, String filename) {
    }
}
