package tech.sioseforge.auth.domain.view;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestVO(
    @NotBlank(message = "ssoId must not be blank")
    String ssoId,
    @NotBlank(message = "username must not be blank")
    String username,
    @NotBlank(message = "password must not be blank")
    @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
    String password
) {}
