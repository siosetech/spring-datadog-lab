package tech.sioseforge.auth.resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.auth.client.UserProfileClient;
import tech.sioseforge.auth.domain.view.TraceCheckResponseVO;
import tech.sioseforge.auth.service.ObservabilityService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/observability")
public class ObservabilityController {

    private final ObservabilityService observabilityService;
    private final UserProfileClient userProfileClient;

    public ObservabilityController(ObservabilityService observabilityService, UserProfileClient userProfileClient) {
        this.observabilityService = observabilityService;
        this.userProfileClient = userProfileClient;
    }

    @GetMapping("/trace-check")
    public TraceCheckResponseVO traceCheck() {
        return observabilityService.checkTrace();
    }

    @GetMapping("/trace-deep")
    public TraceCheckResponseVO traceDeep() {
        return observabilityService.deepTraceCheck();
    }

    @GetMapping("/profile-stats")
    public Map<String, Object> getProfileStats() {
        return userProfileClient.getProfileStats();
    }
}
