package tech.sioseforge.auth.client;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange("/api/v1/profiles")
public interface UserProfileClient {

    @GetExchange("/stats")
    Map<String, Object> getProfileStats();
}
