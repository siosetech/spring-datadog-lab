package tech.sioseforge.notification.resource.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            Span currentSpan = Span.current();
            if (currentSpan != null) {
                currentSpan.setAttribute("tenant.id", tenantId);
                currentSpan.setAttribute("service.layer", "notification-api");
            }
        }
        filterChain.doFilter(request, response);
    }
}
