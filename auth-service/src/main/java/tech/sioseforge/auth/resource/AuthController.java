package tech.sioseforge.auth.resource;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.auth.domain.view.AuthResponseVO;
import tech.sioseforge.auth.domain.view.LoginRequestVO;
import tech.sioseforge.auth.domain.view.PermissionVO;
import tech.sioseforge.auth.domain.view.RegisterRequestVO;
import tech.sioseforge.auth.service.AuthService;
import tech.sioseforge.auth.service.PermissionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionService permissionService;

    public AuthController(AuthService authService, PermissionService permissionService) {
        this.authService = authService;
        this.permissionService = permissionService;
    }

    @PostMapping("/login")
    public AuthResponseVO login(@Valid @RequestBody LoginRequestVO request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public AuthResponseVO register(@Valid @RequestBody RegisterRequestVO request) {
        return authService.register(request);
    }

    @GetMapping("/permissions/{userId}")
    public List<PermissionVO> getPermissions(@PathVariable Long userId) {
        return permissionService.listPermissionsByUserId(userId);
    }
}
