package tech.sioseforge.auth.domain.view;

public record AuthResponseVO(
    String token,
    long expiresIn
) {}
