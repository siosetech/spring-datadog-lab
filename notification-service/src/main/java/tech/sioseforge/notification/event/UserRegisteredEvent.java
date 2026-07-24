package tech.sioseforge.notification.event;

public record UserRegisteredEvent(
        String username,
        String email,
        String timestamp
) {}
