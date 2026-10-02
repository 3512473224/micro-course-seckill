package com.mall.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 极简 Token 工具（演示用）。
 *
 * 新格式：base64url(userId.username.role.expireMillis) + "." + base64url(sha256Hex(payload + ":" + SECRET))
 * 旧格式（userId.username.expireMillis，无 role 字段）依然能验签通过，解析 role 时默认 student，
 * 避免一次全量发版时"老 token 全部失效"的事故。
 *
 * 校验逻辑：验签名防篡改 + 验过期时间。无状态，网关只凭 SECRET 就能验签，不用查 Redis。
 *
 * 生产环境做法：
 *  1. 用 jjwt 等库签发标准 JWT（header.payload.signature）；
 *  2. SECRET 放 Vault / 环境变量，定期轮换；
 *  3. 登出/踢人用 Redis 黑名单（jti -> expire），网关校验时多查一次 Redis；
 *  4. 敏感操作（选课、退课）建议短有效期 access_token + refresh_token 机制。
 */
public final class JwtUtil {

    private static final String SECRET =
            System.getenv().getOrDefault("JWT_SECRET", "mall-dev-secret-please-change-in-prod");
    /** Token 有效期：24 小时 */
    private static final long EXPIRE_MS = 24 * 3600 * 1000L;
    /** 老版本 payload 缺 role 字段时的默认角色，保证兼容 */
    private static final String DEFAULT_ROLE = "student";

    private JwtUtil() {
    }

    /** 新签发：payload 携带角色，网关可直接做角色校验 */
    public static String generate(Long userId, String username, String role) {
        long exp = System.currentTimeMillis() + EXPIRE_MS;
        String payload = userId + "." + username + "." + role + "." + exp;
        String sign = sha256Hex(payload + ":" + SECRET);
        return base64Url(payload) + "." + base64Url(sign);
    }

    /** 兼容旧签发：默认 student 角色 */
    public static String generate(Long userId, String username) {
        return generate(userId, username, DEFAULT_ROLE);
    }

    /** 验签名 + 验过期，任意一步失败都返回 false；兼容 3 字段和 4 字段两种 payload */
    public static boolean verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return false;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String sign = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\.");
            // 兼容：新格式 4 字段（userId.username.role.exp），旧格式 3 字段（userId.username.exp）
            if (fields.length != 3 && fields.length != 4) {
                return false;
            }
            // 1. 过期校验：过期时间永远是最后一个字段
            if (System.currentTimeMillis() > Long.parseLong(fields[fields.length - 1])) {
                return false;
            }
            // 2. 签名校验：防篡改（不知道 SECRET 就伪造不出合法签名）
            return sign.equals(sha256Hex(payload + ":" + SECRET));
        } catch (Exception e) {
            return false;
        }
    }

    /** 调用前请先 verify(token)，这里只做解析 */
    public static Long getUserId(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        return Long.parseLong(payload.split("\\.")[0]);
    }

    /** 解析角色；老 token 无 role 字段时返回 student。调用前请先 verify(token) */
    public static String getRole(String token) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                    StandardCharsets.UTF_8);
            String[] fields = payload.split("\\.");
            if (fields.length == 4) {
                return fields[2];
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_ROLE;
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
