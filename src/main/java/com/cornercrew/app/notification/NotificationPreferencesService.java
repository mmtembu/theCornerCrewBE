package com.cornercrew.app.notification;

public interface NotificationPreferencesService {
    NotificationPreferencesDto updatePreferences(Long userId, UpdateNotificationPreferencesRequest req);
    NotificationPreferencesDto getPreferences(Long userId);
}
