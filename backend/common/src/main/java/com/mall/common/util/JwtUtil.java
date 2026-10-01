package com.mall.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 极简 Token 工具（演示用）。
 *
 * 格式：base64url(userId.username.expireMillis) + "." + base64url(sha256Hex(payload + ":" + SECRET))
 * 校验逻辑：验签名防篡改 + 验过期时间。无状态，网关只凭 SECRET 就能验签，不用查 Redis。
 *
 * 生产环境做法（注释说明，面试常问）：
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

    private JwtUtil() {
    }

    public static String generate(Long userId, String username) {
        long exp = System.currentTimeMillis() + EXPIRE_MS;
        String payload = userId + "." + username + "." + exp;
        String sign = sha256Hex(payload + ":" + SECRET);
        return base64Url(payload) + "." + base64Url(sign);
    }

    /** 验签名 + 验过期，任意一步失败都返回 false */
    public static boolean verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return false;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String sign = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\.");
            if (fields.length != 3) {
                return false;
            }
            // 1. 过期校验
            if (System.currentTimeMillis() > Long.parseLong(fields[2])) {
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
