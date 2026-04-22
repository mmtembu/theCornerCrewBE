package com.cornercrew.app.notification;

import com.cornercrew.app.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/notification-preferences")
public class NotificationPreferencesController {

    private final NotificationPreferencesService notificationPreferencesService;

    public NotificationPreferencesController(NotificationPreferencesService notificationPreferencesService) {
        this.notificationPreferencesService = notificationPreferencesService;
    }

    @PatchMapping
    public ResponseEntity<NotificationPreferencesDto> updatePreferences(
            @RequestBody UpdateNotificationPreferencesRequest request,
            @AuthenticationPrincipal User user) {
        NotificationPreferencesDto dto = notificationPreferencesService.updatePreferences(user.getId(), request);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<NotificationPreferencesDto> getPreferences(
            @AuthenticationPrincipal User user) {
        NotificationPreferencesDto dto = notificationPreferencesService.getPreferences(user.getId());
        return ResponseEntity.ok(dto);
    }
}
