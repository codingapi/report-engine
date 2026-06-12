package com.codingapi.report.excel;

import com.codingapi.report.excel.pojo.FontInfo;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字体注册中心，负责扫描字体目录、解析字体元数据、提供字体清单。
 * <p>
 * 主要职责：
 * <ul>
 *   <li>扫描指定目录下的字体文件（.ttf / .otf / .ttc），解析每个文件的族名和样式</li>
 *   <li>支持双目录：内置字体目录 + 用户自定义字体目录</li>
 *   <li>通过文件名数字前缀排序（如 "01_Arial.ttf" → 1），无编号文件按文件名自然排序</li>
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

    /** 文件名数字前缀正则：匹配 "NN_..." 格式 */
    private static final Pattern ORDER_PREFIX = Pattern.compile("^(\\d+)_.+$");

    /** classpath 下内置字体的资源路径 */
    private static final String BUILTIN_FONTS_RESOURCE = "fonts/";

    /** 内置字体目录（优先扫描） */
    private final Path builtinFontDir;

    /** 用户自定义字体目录（可选） */
    private final Path customFontDir;

    /** 字体清单（扫描后缓存） */
    private volatile List<FontInfo> catalog;

    /** 文件名 → 字体信息映射 */
    private final Map<String, FontInfo> fileMap = new ConcurrentHashMap<>();

    /**
     * 创建字体注册中心实例（单目录模式，向后兼容）。
     *
     * @param fontDir 字体文件目录路径
     */
    public FontRegistry(Path fontDir) {
        this(fontDir, null);
    }

    /**
     * 创建字体注册中心实例（双目录模式）。
     * <p>
     * 内置字体优先加载，自定义字体随后加载。两个目录各自独立排序。
     * </p>
     *
     * @param builtinFontDir 内置字体目录路径
     * @param customFontDir  用户自定义字体目录路径（可为 null）
     */
    public FontRegistry(Path builtinFontDir, Path customFontDir) {
        this.builtinFontDir = builtinFontDir;
        this.customFontDir = customFontDir;
    }

    /**
     * 从 classpath 的 fonts/ 目录加载内置字体。
     * <p>
     * 支持两种运行场景：
     * <ul>
     *   <li>开发模式（file: 协议）：直接返回 classpath 下的字体目录路径</li>
     *   <li>JAR 部署（jar: 协议）：提取到临时目录，临时文件标记为 deleteOnExit</li>
     * </ul>
     * </p>
     *
     * @return 内置字体目录路径
     * @throws IOException 加载失败
     */
    public static Path extractBuiltinFonts() throws IOException {
        ClassLoader cl = FontRegistry.class.getClassLoader();
        URL url = cl.getResource(BUILTIN_FONTS_RESOURCE);

        if (url == null) {
            LOG.warning("classpath 中未找到内置字体资源: " + BUILTIN_FONTS_RESOURCE);
            Path emptyDir = Files.createTempDirectory("report-engine-fonts");
            emptyDir.toFile().deleteOnExit();
            return emptyDir;
        }

        // 开发模式：资源在文件系统上（target/classes/fonts/），直接使用
        if ("file".equals(url.getProtocol())) {
            Path dir = Path.of(url.getPath());
            if (Files.isDirectory(dir)) {
                LOG.info("内置字体目录（开发模式）: " + dir);
                return dir;
            }
        }

        // JAR 部署：资源在 JAR 包内，提取到临时目录
        if ("jar".equals(url.getProtocol())) {
            Path tempDir = Files.createTempDirectory("report-engine-fonts");
            tempDir.toFile().deleteOnExit();

            String jarPath = url.getPath();
            int bangIdx = jarPath.indexOf("!");
            if (bangIdx < 0) return tempDir;

            String filePart = jarPath.substring(5, bangIdx);
            int count = 0;

            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(filePart)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(BUILTIN_FONTS_RESOURCE) || entry.isDirectory()) continue;

                    String fileName = name.substring(BUILTIN_FONTS_RESOURCE.length());
                    if (fileName.isEmpty()) continue;

                    String ext = getExtension(fileName);
                    if (!SUPPORTED_EXTENSIONS.contains(ext)) continue;

                    Path target = tempDir.resolve(fileName);
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        target.toFile().deleteOnExit();
                        count++;
                    }
                }
            }

            LOG.info("内置字体提取完成（JAR 模式）: " + tempDir + "，共 " + count + " 个文件");
            return tempDir;
        }

        LOG.warning("不支持的字体资源协议: " + url.getProtocol());
        Path emptyDir = Files.createTempDirectory("report-engine-fonts");
        emptyDir.toFile().deleteOnExit();
        return emptyDir;
    }

    /**
     * 扫描字体目录，解析所有字体文件的元数据。
     * <p>
     * 先扫描内置字体目录，再扫描用户自定义目录。
     * 使用 {@link Font#createFont(int, java.io.File)} 读取字体文件内嵌的族名和样式信息。
     * .ttc（TrueType Collection）文件仅解析第一个字体。
     * 扫描过程中遇到无法解析的文件会记录警告日志并跳过。
     * </p>
     */
    public void scanDirectory() {
        List<FontInfo> builtinFonts = scanDir(builtinFontDir, "内置");
        List<FontInfo> customFonts = scanDir(customFontDir, "自定义");

        List<FontInfo> result = new ArrayList<>(builtinFonts.size() + customFonts.size());
        result.addAll(builtinFonts);
        result.addAll(customFonts);

        catalog = Collections.unmodifiableList(result);
        LOG.info("字体扫描完成，共 " + result.size() + " 个字体（内置 "
                + builtinFonts.size() + "，自定义 " + customFonts.size() + "）");
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
            Path fontFile = resolveFontFile(info.getFilename());
            if (fontFile == null) continue;

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
     * <p>先查自定义目录，再查内置目录。</p>
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
        return resolveFontFile(filename);
    }

    /**
     * 获取内置字体目录路径。
     *
     * @return 内置字体目录路径
     */
    public Path getBuiltinFontDir() {
        return builtinFontDir;
    }

    /**
     * 获取用户自定义字体目录路径。
     *
     * @return 自定义字体目录路径，未配置时返回 null
     */
    public Path getCustomFontDir() {
        return customFontDir;
    }

    // ─── 内部方法 ─────────────────────────────────────────────

    /**
     * 扫描单个目录下的字体文件。
     */
    private List<FontInfo> scanDir(Path dir, String label) {
        if (dir == null || !Files.isDirectory(dir)) {
            if (dir != null) {
                LOG.fine(label + "字体目录不存在: " + dir);
            }
            return Collections.emptyList();
        }

        List<FontInfo> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;

                String filename = file.getFileName().toString();
                String ext = getExtension(filename);
                if (!SUPPORTED_EXTENSIONS.contains(ext)) continue;

                try {
                    FontInfo info = parseFontFile(file, ext);
                    if (info != null) {
                        result.add(info);
                        fileMap.put(info.getFilename(), info);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "跳过无法解析的字体文件: " + filename, e);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "扫描" + label + "字体目录失败: " + dir, e);
        }

        // 排序：有编号的按编号排，无编号的按文件名自然序排在后面
        result.sort(Comparator
                .comparingInt(FontInfo::getOrder)
                .thenComparing(FontInfo::getFilename));

        LOG.info(label + "字体目录扫描完成: " + dir + "，发现 " + result.size() + " 个字体");
        return result;
    }

    /**
     * 在两个目录中查找字体文件。
     */
    private Path resolveFontFile(String filename) {
        if (customFontDir != null) {
            Path file = customFontDir.resolve(filename);
            if (Files.isRegularFile(file)) return file;
        }
        if (builtinFontDir != null) {
            Path file = builtinFontDir.resolve(filename);
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }

    private FontInfo parseFontFile(Path file, String ext) throws IOException, java.awt.FontFormatException {
        Font awtFont = Font.createFont(Font.TRUETYPE_FONT, file.toFile());

        FontInfo info = new FontInfo();
        info.setFamily(awtFont.getFamily());
        info.setFilename(file.getFileName().toString());
        info.setFormat(ext);
        info.setOrder(parseOrder(info.getFilename()));

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

    /**
     * 从文件名解析数字前缀作为排序值。
     * 如 "01_Arial.ttf" → 1，"10_宋体.ttf" → 10。
     * 无编号的文件返回 Integer.MAX_VALUE，排在最后。
     */
    private static int parseOrder(String filename) {
        Matcher m = ORDER_PREFIX.matcher(filename);
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }
}
