package tech.sioseforge.auth.domain.view;

import jakarta.validation.constraints.NotBlank;

public record DatadogApiKeyRequestVO(
    @NotBlank(message = "apiKey must not be blank")
    String apiKey
) {}
