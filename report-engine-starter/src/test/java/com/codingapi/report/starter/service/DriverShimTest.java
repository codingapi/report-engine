package com.codingapi.report.starter.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** {@link DriverShim} 委托行为测试：每个方法均转发给被包装的真实驱动实例。 */
class DriverShimTest {

    @Test
    void delegatesConnect() throws SQLException {
        RecordingDriver delegate = new RecordingDriver();
        DriverShim shim = new DriverShim(delegate);
        Properties info = new Properties();
        Connection result = shim.connect("jdbc:stub://x", info);
        assertSame(delegate.lastConnectUrl, "jdbc:stub://x");
        assertSame(delegate.lastConnectInfo, info);
        assertSame(delegate.connectReturn, result);
    }

    @Test
    void delegatesAcceptsURL() throws SQLException {
        RecordingDriver delegate = new RecordingDriver();
        delegate.acceptsURLReturn = true;
        DriverShim shim = new DriverShim(delegate);
        assertTrue(shim.acceptsURL("jdbc:stub://x"));
        assertEquals("jdbc:stub://x", delegate.lastAcceptsURL);
    }

    @Test
    void delegatesGetPropertyInfo() throws SQLException {
        RecordingDriver delegate = new RecordingDriver();
        DriverPropertyInfo[] expected = new DriverPropertyInfo[] {new DriverPropertyInfo("k", "v")};
        delegate.propertyInfoReturn = expected;
        DriverShim shim = new DriverShim(delegate);
        DriverPropertyInfo[] actual = shim.getPropertyInfo("jdbc:stub://x", new Properties());
        assertSame(expected, actual);
    }

    @Test
    void delegatesVersionAndCompliance() {
        RecordingDriver delegate = new RecordingDriver();
        delegate.major = 8;
        delegate.minor = 3;
        delegate.compliant = true;
        DriverShim shim = new DriverShim(delegate);
        assertEquals(8, shim.getMajorVersion());
        assertEquals(3, shim.getMinorVersion());
        assertTrue(shim.jdbcCompliant());
    }

    @Test
    void delegatesParentLogger() throws SQLFeatureNotSupportedException {
        RecordingDriver delegate = new RecordingDriver();
        Logger logger = Logger.getLogger("stub");
        delegate.parentLogger = logger;
        DriverShim shim = new DriverShim(delegate);
        assertSame(logger, shim.getParentLogger());
        assertTrue(delegate.parentLoggerCalled);
    }

    @Test
    void delegatesExceptions() {
        RecordingDriver delegate = new RecordingDriver();
        delegate.connectThrows = new SQLException("boom");
        DriverShim shim = new DriverShim(delegate);
        SQLException ex = assertThrows(SQLException.class, () -> shim.connect("u", null));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void delegateAccessorReturnsRealDriver() {
        RecordingDriver delegate = new RecordingDriver();
        DriverShim shim = new DriverShim(delegate);
        assertSame(delegate, shim.delegate());
    }

    /** 记录每次方法调用的 stub Driver。 */
    static class RecordingDriver implements Driver {
        String lastConnectUrl;
        Properties lastConnectInfo;
        Connection connectReturn;
        SQLException connectThrows;
        String lastAcceptsURL;
        boolean acceptsURLReturn;
        DriverPropertyInfo[] propertyInfoReturn;
        int major;
        int minor;
        boolean compliant;
        Logger parentLogger;
        boolean parentLoggerCalled;

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            lastConnectUrl = url;
            lastConnectInfo = info;
            if (connectThrows != null) throw connectThrows;
            return connectReturn;
        }

        @Override
        public boolean acceptsURL(String url) {
            lastAcceptsURL = url;
            return acceptsURLReturn;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return propertyInfoReturn;
        }

        @Override
        public int getMajorVersion() {
            return major;
        }

        @Override
        public int getMinorVersion() {
            return minor;
        }

        @Override
        public boolean jdbcCompliant() {
            return compliant;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            parentLoggerCalled = true;
            return parentLogger;
        }
    }
}
