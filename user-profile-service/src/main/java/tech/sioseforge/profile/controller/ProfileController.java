package tech.sioseforge.profile.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/profiles")
public class ProfileController {

    @GetMapping("/stats")
    public Map<String, Object> getProfileStats() {
        return Map.of(
                "totalProfiles", 150,
                "activeProfiles", 120,
                "service", "user-profile-service"
        );
    }
}
