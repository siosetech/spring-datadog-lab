package tech.sioseforge.gateway.filter;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceContextGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceContextGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        String method = exchange.getRequest().getMethod().name();
        
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            currentSpan.setAttribute("http.method", method);
            currentSpan.setAttribute("http.url", path);
            log.info("Processing request {} {} with TraceId: {}", method, path, currentSpan.getSpanContext().getTraceId());
        } else {
            log.info("Processing request {} {} (No active span context)", method, path);
        }

        // Add custom headers if needed before routing
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header("X-Gateway-Enriched", "true")
                .build())
            .build();

        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            log.info("Finished processing request {} {} with status: {}", 
                method, path, exchange.getResponse().getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        return -1; // High precedence
    }
}
