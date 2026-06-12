package com.codingapi.report.starter.controller;

import com.codingapi.report.excel.FontRegistry;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
     * 返回当前可用的自定义字体列表（已排序、按族名去重）。
     * <p>
     * 每个字体族仅返回第一个文件（目录已按文件名排序，第一个即首选变体）。
     * 不返回内置字体（Univer 已自带，重复添加会抛异常，且 @font-face 会覆盖系统字体）。
     * </p>
     */
    @GetMapping("/list")
    public MultiResponse<FontItem> listFonts() {
        // 收集内置字体的 family 名称（排除与内置同名的自定义字体，避免 @font-face 覆盖系统字体）
        Set<String> builtinFamilies = fontRegistry.getBuiltinFontCatalog().stream()
                .map(f -> f.getFamily().toLowerCase())
                .collect(Collectors.toSet());

        // 返回自定义字体，按族名去重，排除内置字体
        Set<String> seen = new HashSet<>();
        return MultiResponse.of(fontRegistry.getCustomFontCatalog().stream()
                .filter(f -> !builtinFamilies.contains(f.getFamily().toLowerCase()))
                .filter(f -> seen.add(f.getFamily()))
                .map(f -> new FontItem(f.getFamily(), f.getFilename()))
                .collect(Collectors.toList()));
    }

    /**
     * 字体列表响应项。
     *
     * @param family   字体族名（用于 CSS font-family 和 Univer value）
     * @param filename 字体文件名（供前端按需加载字体文件时使用）
     */
    /**
     * 下载字体文件，供前端通过 @font-face 加载。
     */
    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> downloadFont(@PathVariable String filename) {
        Path fontFile = fontRegistry.getFontFile(filename);
        if (fontFile == null) {
            return ResponseEntity.notFound().build();
        }

        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Cache-Control", "public, max-age=31536000")
                .body(new FileSystemResource(fontFile));
    }

    private static final Map<String, String> MIME_TYPES = Map.of(
            "ttf", "font/ttf",
            "otf", "font/otf",
            "ttc", "font/collection"
    );

    public record FontItem(String family, String filename) {
    }
}
