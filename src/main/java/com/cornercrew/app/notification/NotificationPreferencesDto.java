package com.cornercrew.app.notification;

public record NotificationPreferencesDto(
    boolean commuteNotificationsEnabled,
    boolean jobNotificationsEnabled
) {}
