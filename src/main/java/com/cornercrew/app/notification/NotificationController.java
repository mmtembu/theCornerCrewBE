package com.cornercrew.app.notification;

import com.cornercrew.app.assignment.ApplicationDto;
import com.cornercrew.app.assignment.ApplicationService;
import com.cornercrew.app.assignment.ApplyRequest;
import com.cornercrew.app.common.CampaignNotAcceptingApplicationsException;
import com.cornercrew.app.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ApplicationService applicationService;
    private final ObjectMapper objectMapper;

    public NotificationController(NotificationService notificationService,
                                  ApplicationService applicationService,
                                  ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.applicationService = applicationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationDto>> listNotifications(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean read,
            Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<NotificationDto> page = notificationService.listNotifications(user.getId(), type, read, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User user) {
        long count = notificationService.countUnread(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        NotificationDto dto = notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{id}/dismiss")
    public ResponseEntity<NotificationDto> dismiss(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        NotificationDto dto = notificationService.dismiss(id, user.getId());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApplicationDto> acceptNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        // 1. Get notification and validate ownership
        NotificationDto notification = notificationService.getNotification(id, user.getId());

        // 2. Validate notification type is JOB_AVAILABLE
        if (notification.type() != NotificationType.JOB_AVAILABLE) {
            throw new IllegalArgumentException("Only JOB_AVAILABLE notifications can be accepted");
        }

        // 3. Extract campaignId from metadata JSON
        Long campaignId = extractCampaignId(notification.metadata());

        // 4. Apply to the campaign (creates PENDING application)
        ApplicationDto applicationDto;
        try {
            applicationDto = applicationService.apply(campaignId, user.getId(),
                    new ApplyRequest("Accepted via notification"));
        } catch (IllegalStateException ex) {
            // Campaign is not OPEN or FUNDED — convert to 422
            throw new CampaignNotAcceptingApplicationsException(ex.getMessage());
        }

        // 5. Mark notification as read
        notificationService.markAsRead(id, user.getId());

        // 6. Return 201 with the application
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationDto);
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<NotificationDto> declineNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        NotificationDto dto = notificationService.dismiss(id, user.getId());
        return ResponseEntity.ok(dto);
    }

    private Long extractCampaignId(String metadata) {
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode campaignIdNode = node.get("campaignId");
            if (campaignIdNode == null || campaignIdNode.isNull()) {
                throw new IllegalArgumentException("Notification metadata does not contain campaignId");
            }
            return campaignIdNode.asLong();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse notification metadata: " + ex.getMessage());
        }
    }
}
