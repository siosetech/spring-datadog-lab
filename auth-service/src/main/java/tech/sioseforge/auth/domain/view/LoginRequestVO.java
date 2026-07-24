package tech.sioseforge.auth.domain.view;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestVO(
    @NotBlank(message = "username must not be blank")
    String username,
    @NotBlank(message = "password must not be blank")
    String password
) {}
