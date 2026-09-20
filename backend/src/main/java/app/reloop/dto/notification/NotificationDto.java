package app.reloop.dto.notification;

import app.reloop.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String type,
        String title,
        String message,
        UUID referenceId,
        boolean read,
        Instant createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getReferenceId(), notification.isRead(),
                notification.getCreatedAt());
    }
}
