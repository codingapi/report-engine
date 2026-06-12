package com.codingapi.report.excel;

import com.codingapi.report.excel.pojo.FontInfo;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 字体注册中心，负责扫描字体目录、解析字体元数据、提供字体清单。
 * <p>
 * 主要职责：
 * <ul>
 *   <li>扫描指定目录下的字体文件（.ttf / .otf / .ttc），解析每个文件的族名和样式</li>
 *   <li>提供字体目录清单（{@link #getFontCatalog()}），供前端按需加载</li>
 *   <li>可选地将字体注册到 JVM GraphicsEnvironment，供 POI 文本度量使用</li>
 * </ul>
 * 此类为纯 Java 实现，不依赖任何 Web 框架。
 * </p>
 */
public class FontRegistry {

    private static final Logger LOG = Logger.getLogger(FontRegistry.class.getName());

    /** 支持的字体文件扩展名 */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("ttf", "otf", "ttc");

    /** 字体目录路径 */
    private final Path fontDir;

    /** 字体清单（扫描后缓存） */
    private volatile List<FontInfo> catalog;

    /** 文件名 → 字体信息映射 */
    private final Map<String, FontInfo> fileMap = new ConcurrentHashMap<>();

    /**
     * 创建字体注册中心实例。
     *
     * @param fontDir 字体文件目录路径
     */
    public FontRegistry(Path fontDir) {
        this.fontDir = fontDir;
    }

    /**
     * 扫描字体目录，解析所有字体文件的元数据。
     * <p>
     * 使用 {@link Font#createFont(int, java.io.File)} 读取字体文件内嵌的族名和样式信息。
     * .ttc（TrueType Collection）文件仅解析第一个字体。
     * 扫描过程中遇到无法解析的文件会记录警告日志并跳过。
     * </p>
     */
    public void scanDirectory() {
        if (!Files.isDirectory(fontDir)) {
            LOG.warning("字体目录不存在: " + fontDir);
            catalog = Collections.emptyList();
            return;
        }

        List<FontInfo> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(fontDir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;

                String ext = getExtension(file.getFileName().toString());
                if (!SUPPORTED_EXTENSIONS.contains(ext)) continue;

                try {
                    FontInfo info = parseFontFile(file, ext);
                    if (info != null) {
                        result.add(info);
                        fileMap.put(info.getFilename(), info);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "跳过无法解析的字体文件: " + file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "扫描字体目录失败: " + fontDir, e);
        }

        catalog = Collections.unmodifiableList(result);
        LOG.info("字体目录扫描完成: " + fontDir + "，发现 " + result.size() + " 个字体");
    }

    /**
     * 将已扫描的字体注册到 JVM 的 GraphicsEnvironment。
     * <p>
     * 注册后，Java 2D 文本渲染（包括 POI 的文本度量计算）可以使用这些字体。
     * 仅在需要精确文本度量时调用，普通的 Excel 导出（仅写字体名称）不需要此操作。
     * </p>
     */
    public void registerToGraphicsEnvironment() {
        if (catalog == null) {
            scanDirectory();
        }

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        int count = 0;

        for (FontInfo info : catalog) {
            Path fontFile = fontDir.resolve(info.getFilename());
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile());
                ge.registerFont(font);
                count++;
            } catch (Exception e) {
                LOG.log(Level.FINE, "注册字体到 GraphicsEnvironment 失败: " + info.getFilename(), e);
            }
        }

        LOG.info("已注册 " + count + " 个字体到 GraphicsEnvironment");
    }

    /**
     * 获取字体目录清单。如果尚未扫描，会先触发扫描。
     *
     * @return 不可变的字体信息列表
     */
    public List<FontInfo> getFontCatalog() {
        if (catalog == null) {
            scanDirectory();
        }
        return catalog;
    }

    /**
     * 根据文件名获取字体文件的完整路径。
     *
     * @param filename 字体文件名
     * @return 字体文件路径，如果文件不存在返回 null
     */
    public Path getFontFile(String filename) {
        if (filename == null) return null;
        // 安全检查：防止路径穿越
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return null;
        }
        Path file = fontDir.resolve(filename);
        return Files.isRegularFile(file) ? file : null;
    }

    /**
     * 获取字体目录路径。
     *
     * @return 字体目录路径
     */
    public Path getFontDir() {
        return fontDir;
    }

    // ─── 内部方法 ─────────────────────────────────────────────

    private FontInfo parseFontFile(Path file, String ext) throws IOException, java.awt.FontFormatException {
        // .ttc 文件包含多个字体，仅解析第一个
        Font awtFont = Font.createFont(Font.TRUETYPE_FONT, file.toFile());

        FontInfo info = new FontInfo();
        info.setFamily(awtFont.getFamily());
        info.setFilename(file.getFileName().toString());
        info.setFormat(ext);

        int awtStyle = awtFont.getStyle();
        boolean bold = (awtStyle & Font.BOLD) != 0;
        boolean italic = (awtStyle & Font.ITALIC) != 0;

        if (bold && italic) {
            info.setStyle("bold-italic");
        } else if (bold) {
            info.setStyle("bold");
        } else if (italic) {
            info.setStyle("italic");
        } else {
            info.setStyle("regular");
        }

        return info;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }
}
