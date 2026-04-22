package com.cornercrew.app.notification;

import java.time.OffsetDateTime;

public record NotificationDto(
        Long id,
        Long userId,
        NotificationType type,
        String title,
        String body,
        String metadata,
        String actionUrl,
        OffsetDateTime createdAt,
        OffsetDateTime readAt,
        OffsetDateTime dismissedAt
) {}
