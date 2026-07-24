package tech.sioseforge.auth.event;

public record UserRegisteredEvent(
        String username,
        String email,
        String timestamp
) {}
