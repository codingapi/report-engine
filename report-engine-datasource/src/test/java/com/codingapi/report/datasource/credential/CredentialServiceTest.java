package com.codingapi.report.datasource.credential;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialServiceTest {

    private final CredentialService service = new CredentialService("test-key");

    @Test
    void encryptThenDecryptRestoresPlainText() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("host", "localhost");
        config.put("password", "s3cret");

        Map<String, Object> encrypted = service.encryptConfig(config);
        assertEquals("localhost", encrypted.get("host"));
        assertNotEquals("s3cret", encrypted.get("password"));
        assertTrue(((String) encrypted.get("password")).startsWith("enc:"));

        Map<String, Object> decrypted = service.decryptConfig(encrypted);
        assertEquals("s3cret", decrypted.get("password"));
        assertEquals("localhost", decrypted.get("host"));
    }

    @Test
    void encryptIsIdempotent() {
        Map<String, Object> config = Map.of("password", "abc");
        Map<String, Object> once = service.encryptConfig(config);
        Map<String, Object> twice = service.encryptConfig(once);
        assertEquals(once.get("password"), twice.get("password"));
    }

    @Test
    void maskReplacesAllSensitiveValues() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("username", "admin");
        config.put("password", "enc:something");
        config.put("token", "xyz");
        Map<String, Object> masked = service.maskConfig(config);
        assertEquals("admin", masked.get("username"));
        assertEquals("***", masked.get("password"));
        assertEquals("***", masked.get("token"));
    }

    @Test
    void isMaskedDetectsPlaceholder() {
        assertTrue(service.isMasked("***"));
        assertFalse(service.isMasked("real"));
    }

    @Test
    void emptyPasswordNotEncrypted() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("password", "");
        Map<String, Object> encrypted = service.encryptConfig(config);
        assertEquals("", encrypted.get("password"));
    }
}
