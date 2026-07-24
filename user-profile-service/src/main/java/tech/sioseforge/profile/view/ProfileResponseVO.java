package tech.sioseforge.profile.view;

public record ProfileResponseVO(
    Long userId,
    String username,
    String status,
    String lastUpdatedAt
) {
}
