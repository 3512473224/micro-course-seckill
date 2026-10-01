package com.mall.gateway.filter;

import com.mall.common.constant.SecurityConstants;
import com.mall.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器：所有经过网关的请求都要过这一关。
 *
 * 为什么在网关做鉴权而不是每个服务自己做？
 *  1. 收口：登录态校验逻辑只写一处，5 个服务不用重复实现，也不会出现"某个服务漏验"的事故；
 *  2. 下游服务可以专注业务；网关验签通过后把 userId 写入 X-User-Id 透传，下游直接信任；
 *  3. 非法请求在入口就被 401 挡掉，打不到业务服务，天然抗一波攻击/爬虫。
 * 代价：网关成了单点，要做多实例 + 前面挂 LB（生产常规做法）。
 */
@Slf4j
@Component
public class GlobalAuthFilter implements GlobalFilter, Ordered {

    /** 登录接口白名单：不需要 Token */
    private static final List<String> WHITE_LIST = List.of(
            "/api/user/login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2. 校验 Authorization: Bearer <token>
        String auth = exchange.getRequest().getHeaders().getFirst(SecurityConstants.AUTH_HEADER);
        if (auth == null || !auth.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange, "缺少登录凭证，请先登录");
        }
        String token = auth.substring(SecurityConstants.TOKEN_PREFIX.length());
        if (!JwtUtil.verify(token)) {
            return unauthorized(exchange, "登录已过期或凭证无效，请重新登录");
        }

        // 3. 验签通过：把 userId 透传给下游服务（下游从 X-User-Id 取，不用再解析 Token）
        Long userId = JwtUtil.getUserId(token);
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(SecurityConstants.USER_ID_HEADER, String.valueOf(userId))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /** WebFlux 里写回 401 JSON：不能像 MVC 那样直接 return，必须拼 DataBuffer */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        log.warn("网关拦截未授权请求: {} reason={}", exchange.getRequest().getURI().getPath(), message);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** order 越小越先执行；-100 保证在 NettyRoutingFilter（转发）之前完成鉴权 */
    @Override
    public int getOrder() {
        return -100;
    }
}
