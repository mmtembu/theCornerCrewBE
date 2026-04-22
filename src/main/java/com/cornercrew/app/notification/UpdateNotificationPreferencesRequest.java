package com.cornercrew.app.notification;

public record UpdateNotificationPreferencesRequest(
    Boolean commuteNotificationsEnabled,
    Boolean jobNotificationsEnabled
) {}
