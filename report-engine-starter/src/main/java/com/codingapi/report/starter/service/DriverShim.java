package com.codingapi.report.starter.service;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 委托型 {@link Driver} 适配器：让 URLClassLoader 加载的外部驱动通过 {@link java.sql.DriverManager#registerDriver}
 * 注册后被 DriverManager 正常发现。
 *
 * <p>背景：{@link java.sql.DriverManager} 的过滤逻辑只信任「系统类加载器加载的 Driver」。直接把外部 jar 加载的 Driver
 * 实例注册进去，{@code getConnection} 时会被忽略。包一层 shim（本类由系统类加载器加载），所有方法委托给 真实驱动实例即可绕过该限制。
 */
class DriverShim implements Driver {

    private final Driver delegate;

    DriverShim(Driver delegate) {
        this.delegate = delegate;
    }

    /** 被包装的真实驱动实例（测试与诊断用）。 */
    Driver delegate() {
        return delegate;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return delegate.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
