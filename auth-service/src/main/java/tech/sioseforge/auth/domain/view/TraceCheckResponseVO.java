package tech.sioseforge.auth.domain.view;

public record TraceCheckResponseVO(
    String status,
    String message,
    String traceId
) {}
