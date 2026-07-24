package tech.sioseforge.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.dashboard.service.AggregationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final AggregationService aggregationService;
    private final Counter summaryViewsCounter;

    public DashboardController(AggregationService aggregationService, MeterRegistry registry) {
        this.aggregationService = aggregationService;
        this.summaryViewsCounter = Counter.builder("dashboard.summary.views")
                .description("Number of times the dashboard summary was viewed")
                .tag("service", "dashboard-service")
                .register(registry);
    }

    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary() {
        summaryViewsCounter.increment();
        return Map.of(
                "profiles", aggregationService.getProfileStats(),
                "audits", aggregationService.getAuditStats()
        );
    }
}
