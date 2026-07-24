package tech.sioseforge.auth.resource;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.auth.domain.view.DatadogApiKeyRequestVO;
import tech.sioseforge.auth.service.VaultSecretService;

@RestController
@RequestMapping("/api/v1/admin/vault")
public class VaultAdminController {

    private final VaultSecretService vaultSecretService;

    public VaultAdminController(VaultSecretService vaultSecretService) {
        this.vaultSecretService = vaultSecretService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/datadog-api-key")
    public void configureDatadogApiKey(@Valid @RequestBody DatadogApiKeyRequestVO request) {
        vaultSecretService.writeDatadogApiKey(request.apiKey());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/datadog-api-key")
    public String readDatadogApiKey() {
        return vaultSecretService.readDatadogApiKey();
    }
}
