package com.codingapi.report.data.datasource.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * 凭证服务：对 {@code DataSource.config} 中的敏感字段做对称加密 / 解密 / 脱敏。
 *
 * <h3>三态</h3>
 *
 * <ul>
 *   <li><b>持久态</b>：敏感值为 {@code "enc:" + Base64(IV + ciphertext)}（加密）
 *   <li><b>运行态</b>：渲染/探查时解密回明文
 *   <li><b>出口态</b>：API 返回时敏感值替 {@code "***"}（脱敏），无论是否已加密
 * </ul>
 *
 * <h3>密钥</h3>
 *
 * <p>密钥从配置 {@code report.datasource.crypto.key} 注入；缺省用开发期默认值并告警（生产必须覆盖）。 用 SHA-256 派生 32 字节 AES-256
 * 密钥。{@code "enc:"} 前缀标记避免重复加密 / 误判明文。
 *
 * <p>用 JDK {@code javax.crypto}（AES/GCM/NoPadding）而非 commons-crypto：后者是 JNA 加速的低层 API， 需自行管理 IV/Key
 * 派生，对本场景过重；JDK 自带零依赖、足够安全。
 */
@Slf4j
public class CredentialService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final String PREFIX = "enc:";
    private static final String MASK = "***";

    /** 敏感 key 名集合（小写匹配）。新增连接类型若有别的命名约定，扩展此集即可。 */
    private static final Set<String> SENSITIVE_KEYS =
            Set.of(
                    "password",
                    "pwd",
                    "secret",
                    "token",
                    "apikey",
                    "apiKey",
                    "credential",
                    "credentials");

    private final SecretKeySpec key;

    public CredentialService(String secret) {
        String s =
                (secret == null || secret.isBlank()) ? "report-engine-default-crypto-key" : secret;
        if (secret == null || secret.isBlank()) {
            log.warn("未配置 report.datasource.crypto.key，凭证加密使用开发期默认密钥，生产环境必须覆盖");
        }
        this.key = new SecretKeySpec(sha256(s), "AES");
    }

    /** 加密 config 中的敏感 String 值（已带 {@code enc:} 前缀的不重复加密）。 */
    public Map<String, Object> encryptConfig(Map<String, Object> config) {
        if (config == null) return null;
        Map<String, Object> out = new LinkedHashMap<>(config);
        for (Map.Entry<String, Object> e : out.entrySet()) {
            if (isSensitive(e.getKey())
                    && e.getValue() instanceof String v
                    && !v.startsWith(PREFIX)
                    && !v.isEmpty()) {
                e.setValue(encrypt(v));
            }
        }
        return out;
    }

    /** 解密 config 中带 {@code enc:} 前缀的值；非加密值原样返回。 */
    public Map<String, Object> decryptConfig(Map<String, Object> config) {
        if (config == null) return null;
        Map<String, Object> out = new LinkedHashMap<>(config);
        for (Map.Entry<String, Object> e : out.entrySet()) {
            if (e.getValue() instanceof String v && v.startsWith(PREFIX)) {
                e.setValue(decrypt(v));
            }
        }
        return out;
    }

    /** 脱敏：敏感 key 的值一律替 {@code "***"}（不论是否加密）。 */
    public Map<String, Object> maskConfig(Map<String, Object> config) {
        if (config == null) return null;
        Map<String, Object> out = new LinkedHashMap<>(config);
        for (Map.Entry<String, Object> e : out.entrySet()) {
            if (isSensitive(e.getKey()) && e.getValue() != null) {
                e.setValue(MASK);
            }
        }
        return out;
    }

    /** 判断值是否为脱敏占位（{@code "***"}），用于 save 时回填旧值。 */
    public boolean isMasked(Object value) {
        return MASK.equals(value);
    }

    public boolean isSensitive(String keyName) {
        return keyName != null && SENSITIVE_KEYS.contains(keyName.toLowerCase());
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("凭证加密失败", e);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("凭证解密失败", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
