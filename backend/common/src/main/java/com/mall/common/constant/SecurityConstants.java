package com.mall.common.constant;

/**
 * 网关 <-> 服务之间的安全约定。
 * 网关验签通过后，把 userId 写入 X-User-Id 透传给下游；下游服务信任内网 + 该头。
 * （生产：内网服务间加 mTLS / 内部签名，防止有人绕过网关直调服务伪造 X-User-Id）
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final String AUTH_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    /** 网关鉴权通过后透传的用户 id */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** 网关鉴权通过后透传的用户角色（student/teacher/admin） */
    public static final String USER_ROLE_HEADER = "X-User-Role";
}
