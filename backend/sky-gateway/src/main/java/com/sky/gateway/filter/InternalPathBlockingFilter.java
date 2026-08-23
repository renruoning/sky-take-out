package com.sky.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * /internal/**是给服务间RPC用的（比如sky-invoice-service用Feign查sky-server的订单摘要），
 * 不带任何用户/员工token鉴权——不能让外部客户端直接从Gateway访问到，否则等于"不登录就能查任意订单的
 * userId/金额/支付状态"这种数据泄漏。内部服务间调用本来就是Feign通过Nacos直连目标实例，不经过Gateway，
 * 所以这里拦掉不影响真正的服务间调用，只挡外部试图绕过业务接口直接打内部接口的请求。
 */
@Component
public class InternalPathBlockingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path != null && path.startsWith("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
