package tech.sioseforge.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "audit-log-service", url = "${audit-log-service.url:http://localhost:9083}")
public interface AuditLogClient {

    @GetMapping("/api/v1/audits")
    List<Object> getRecentLogs();
}
