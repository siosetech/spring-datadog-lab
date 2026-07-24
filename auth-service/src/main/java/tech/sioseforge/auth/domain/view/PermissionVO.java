package tech.sioseforge.auth.domain.view;

public record PermissionVO(
    Long dashboardId,
    String dashboardName,
    String accessLevel
) {}
