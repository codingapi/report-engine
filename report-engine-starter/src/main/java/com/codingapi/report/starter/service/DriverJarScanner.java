package com.codingapi.report.starter.service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 驱动 jar 扫描器：解析 jar 内 {@link Driver} 实现类名列表。
 *
 * <p>优先读 {@code META-INF/services/java.sql.Driver}（ServiceLoader 机制，JDBC 驱动通用约定）；
 * 文件不存在或为空时回退到扫描 jar 内 {@code .class} 条目，用 {@link URLClassLoader} 加载并按
 * {@code Driver.class.isAssignableFrom(...)} 判定。回退路径会跳过接口、抽象类、自身及加载失败的类。
 */
public class DriverJarScanner {

    private static final String SERVICE_FILE = "META-INF/services/java.sql.Driver";

    public DriverJarScanResult scan(Path jarPath) throws IOException {
        if (!Files.isRegularFile(jarPath)) {
            throw new IOException("jar file not found: " + jarPath);
        }
        String jarFile = jarPath.getFileName().toString();
        Set<String> result = new LinkedHashSet<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry serviceEntry = jar.getJarEntry(SERVICE_FILE);
            if (serviceEntry != null) {
                try (InputStream in = jar.getInputStream(serviceEntry)) {
                    result.addAll(readServiceFile(in));
                }
            }
        }

        if (result.isEmpty()) {
            result.addAll(scanClasses(jarPath));
        }

        return new DriverJarScanResult(jarFile, new ArrayList<>(result));
    }

    private List<String> readServiceFile(InputStream in) throws IOException {
        List<String> classes = new ArrayList<>();
        byte[] bytes = in.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        for (String raw : content.split("\\R")) {
            int hash = raw.indexOf('#');
            if (hash >= 0) {
                raw = raw.substring(0, hash);
            }
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                classes.add(trimmed);
            }
        }
        return classes;
    }

    private List<String> scanClasses(Path jarPath) throws IOException {
        List<String> classes = new ArrayList<>();
        URL jarUrl = jarPath.toUri().toURL();
        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {jarUrl}, getClass().getClassLoader())) {
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) {
                        continue;
                    }
                    String className =
                            name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    try {
                        Class<?> candidate = loader.loadClass(className);
                        if (isConcreteDriver(candidate)) {
                            classes.add(className);
                        }
                    } catch (Throwable ignored) {
                        // 跳过加载失败的类（依赖缺失等）
                    }
                }
            }
        }
        return classes;
    }

    private static boolean isConcreteDriver(Class<?> candidate) {
        if (candidate == null || candidate == Driver.class) {
            return false;
        }
        if (!Driver.class.isAssignableFrom(candidate)) {
            return false;
        }
        int mods = candidate.getModifiers();
        return !candidate.isInterface() && !Modifier.isAbstract(mods);
    }

    public record DriverJarScanResult(String jarFile, List<String> driverClasses) {}
}
