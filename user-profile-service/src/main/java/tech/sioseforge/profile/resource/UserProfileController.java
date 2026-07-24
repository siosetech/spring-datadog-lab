package tech.sioseforge.profile.resource;

import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import tech.sioseforge.profile.service.DefaultProfileService;
import tech.sioseforge.profile.view.ProfileResponseVO;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1/profiles")
public class UserProfileController {

    private final DefaultProfileService profileService;

    public UserProfileController(DefaultProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}")
    public ProfileResponseVO getProfile(@PathVariable @Positive Long userId) {
        return profileService.getProfileByUserId(userId);
    }

    @GetMapping("/stats")
    public Map<String, Object> getProfileStats() {
        return profileService.getProfileStats();
    }
}
