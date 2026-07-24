package tech.sioseforge.auth.domain.view;

public record UserProfileVO(
    Long userId,
    String username,
    String ssoId,
    String email,
    String firstName,
    String lastName
) {}
