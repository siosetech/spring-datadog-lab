package tech.sioseforge.auth.resource.filter;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantSpanEnricherFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = request.getHeader(TenantMdcFilter.TENANT_ID_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            Span currentSpan = Span.current();
            if (currentSpan != null) {
                currentSpan.setAttribute("tenant.id", tenantId);
            }
        }
        filterChain.doFilter(request, response);
    }
}
