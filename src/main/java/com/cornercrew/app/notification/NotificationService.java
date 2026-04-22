package com.cornercrew.app.notification;

import com.cornercrew.app.campaign.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void onCampaignOpened(Campaign campaign);

    Page<NotificationDto> listNotifications(Long userId, NotificationType type, Boolean read, Pageable pageable);

    long countUnread(Long userId);

    NotificationDto markAsRead(Long notificationId, Long userId);

    NotificationDto dismiss(Long notificationId, Long userId);

    NotificationDto getNotification(Long notificationId, Long userId);
}
