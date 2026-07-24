package tech.sioseforge.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "user-profile-service", url = "${user-profile-service.url:http://localhost:9082}")
public interface UserProfileClient {

    @GetMapping("/api/v1/profiles/stats")
    Map<String, Object> getProfileStats();
}
