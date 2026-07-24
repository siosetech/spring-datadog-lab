package tech.sioseforge.auth.resource.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantMdcFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_MDC_KEY = "tenant_id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            MDC.put(TENANT_ID_MDC_KEY, tenantId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TENANT_ID_MDC_KEY);
        }
    }
}
