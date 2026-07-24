package tech.sioseforge.audit.event;

public record UserRegisteredEvent(
        String username,
        String email,
        String timestamp
) {}
