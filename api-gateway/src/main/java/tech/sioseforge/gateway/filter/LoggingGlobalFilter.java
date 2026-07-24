package tech.sioseforge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        log.info("API Gateway Routing Request -> Path: {}", path);
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            log.info("API Gateway Routing Response <- Path: {}, Status: {}",
                    path,
                    exchange.getResponse().getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
