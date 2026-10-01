package com.mall.common.feign;

import com.mall.common.constant.SecurityConstants;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 调用时把当前请求的 Authorization 头透传给下游服务。
 * 为什么需要：服务间调用（如 course-service -> enroll-service）发生在一次用户请求之内，
 * 下游如果要做细粒度鉴权/审计，需要知道是谁在操作。RequestContextHolder 拿到的是
 * 当前线程绑定的 HttpServletRequest（只在同步 Web 请求线程内有效，异步线程拿不到，
 * 此时直接跳过透传，避免 NPE）。
 *
 * 使用方式：各服务的 @SpringBootApplication 上 @Import(FeignConfig.class)。
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor authForwardInterceptor() {
        return template -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String auth = attrs.getRequest().getHeader(SecurityConstants.AUTH_HEADER);
                if (auth != null && !auth.isEmpty()) {
                    template.header(SecurityConstants.AUTH_HEADER, auth);
                }
            }
        };
    }
}
