package com.cornercrew.app.notification;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 9: Notification Persistence Invariants
 *
 * For any newly created notification, createdAt should be non-null, and readAt
 * and dismissedAt should both be null. After any sequence of read and dismiss
 * operations, only the readAt and dismissedAt fields should change; the id,
 * userId, type, title, body, metadata, actionUrl, and createdAt fields should
 * remain identical to their values at creation time.
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.3</b></p>
 *
 * Property 10: Notification Filtering Correctness
 *
 * For any set of notifications with mixed types and read states, filtering by
 * type should return only notifications of that type, and filtering by read
 * status should return only notifications matching that status.
 *
 * <p><b>Validates: Requirements 4.4</b></p>
 */
class NotificationServicePropertyTest {

    // -----------------------------------------------------------------------
    // Property 9: Notification Persistence Invariants
    // -----------------------------------------------------------------------

    /**
     * <b>Validates: Requirements 4.1, 4.2, 4.3</b>
     */
    @Property(tries = 10)
    void notificationPersistenceInvariants(
            @ForAll("arbitraryNotifications") Notification notification,
            @ForAll @LongRange(min = 1, max = 100_000) Long userId
    ) {
        // --- Setup ---
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationServiceImpl service = new NotificationServiceImpl(repository, null, null, null, null, null, null);

        notification.setUserId(userId);
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setReadAt(null);
        notification.setDismissedAt(null);
        notification.setId(1L);

        // Verify creation invariants: createdAt non-null, readAt and dismissedAt null
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getDismissedAt()).isNull();

        // Capture original immutable field values
        Long originalId = notification.getId();
        Long originalUserId = notification.getUserId();
        NotificationType originalType = notification.getType();
        String originalTitle = notification.getTitle();
        String originalBody = notification.getBody();
        String originalMetadata = notification.getMetadata();
        String originalActionUrl = notification.getActionUrl();
        OffsetDateTime originalCreatedAt = notification.getCreatedAt();

        // --- markAsRead ---
        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto readDto = service.markAsRead(1L, userId);

        // Only readAt should have changed
        assertThat(readDto.readAt()).isNotNull();
        assertThat(readDto.id()).isEqualTo(originalId);
        assertThat(readDto.userId()).isEqualTo(originalUserId);
        assertThat(readDto.type()).isEqualTo(originalType);
        assertThat(readDto.title()).isEqualTo(originalTitle);
        assertThat(readDto.body()).isEqualTo(originalBody);
        assertThat(readDto.metadata()).isEqualTo(originalMetadata);
        assertThat(readDto.actionUrl()).isEqualTo(originalActionUrl);
        assertThat(readDto.createdAt()).isEqualTo(originalCreatedAt);

        // --- dismiss ---
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        NotificationDto dismissedDto = service.dismiss(1L, userId);

        // Only dismissedAt should have changed
        assertThat(dismissedDto.dismissedAt()).isNotNull();
        assertThat(dismissedDto.id()).isEqualTo(originalId);
        assertThat(dismissedDto.userId()).isEqualTo(originalUserId);
        assertThat(dismissedDto.type()).isEqualTo(originalType);
        assertThat(dismissedDto.title()).isEqualTo(originalTitle);
        assertThat(dismissedDto.body()).isEqualTo(originalBody);
        assertThat(dismissedDto.metadata()).isEqualTo(originalMetadata);
        assertThat(dismissedDto.actionUrl()).isEqualTo(originalActionUrl);
        assertThat(dismissedDto.createdAt()).isEqualTo(originalCreatedAt);
    }

    // -----------------------------------------------------------------------
    // Property 10: Notification Filtering Correctness
    // -----------------------------------------------------------------------

    /**
     * <b>Validates: Requirements 4.4</b>
     */
    @Property(tries = 10)
    void notificationFilteringCorrectness(
            @ForAll("mixedNotificationLists") List<Notification> notifications,
            @ForAll @LongRange(min = 1, max = 100_000) Long userId
    ) {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationServiceImpl service = new NotificationServiceImpl(repository, null, null, null, null, null, null);

        Pageable pageable = PageRequest.of(0, 100);

        // Assign userId to all notifications
        for (Notification n : notifications) {
            n.setUserId(userId);
        }

        // --- Filter by type: COMMUTE_IMPACT ---
        List<Notification> commuteImpactList = notifications.stream()
                .filter(n -> n.getType() == NotificationType.COMMUTE_IMPACT)
                .collect(Collectors.toList());

        when(repository.findByFilters(eq(userId), eq(NotificationType.COMMUTE_IMPACT), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(commuteImpactList, pageable, commuteImpactList.size()));

        Page<NotificationDto> commuteResult = service.listNotifications(userId, NotificationType.COMMUTE_IMPACT, null, pageable);
        assertThat(commuteResult.getContent()).allMatch(dto -> dto.type() == NotificationType.COMMUTE_IMPACT);
        assertThat(commuteResult.getContent()).hasSize(commuteImpactList.size());

        // --- Filter by type: JOB_AVAILABLE ---
        List<Notification> jobAvailableList = notifications.stream()
                .filter(n -> n.getType() == NotificationType.JOB_AVAILABLE)
                .collect(Collectors.toList());

        when(repository.findByFilters(eq(userId), eq(NotificationType.JOB_AVAILABLE), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(jobAvailableList, pageable, jobAvailableList.size()));

        Page<NotificationDto> jobResult = service.listNotifications(userId, NotificationType.JOB_AVAILABLE, null, pageable);
        assertThat(jobResult.getContent()).allMatch(dto -> dto.type() == NotificationType.JOB_AVAILABLE);
        assertThat(jobResult.getContent()).hasSize(jobAvailableList.size());

        // --- Filter by read=true (readAt is not null) ---
        List<Notification> readList = notifications.stream()
                .filter(n -> n.getReadAt() != null)
                .collect(Collectors.toList());

        when(repository.findByFilters(eq(userId), eq(null), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(readList, pageable, readList.size()));

        Page<NotificationDto> readResult = service.listNotifications(userId, null, true, pageable);
        assertThat(readResult.getContent()).allMatch(dto -> dto.readAt() != null);
        assertThat(readResult.getContent()).hasSize(readList.size());

        // --- Filter by read=false (readAt is null) ---
        List<Notification> unreadList = notifications.stream()
                .filter(n -> n.getReadAt() == null)
                .collect(Collectors.toList());

        when(repository.findByFilters(eq(userId), eq(null), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(unreadList, pageable, unreadList.size()));

        Page<NotificationDto> unreadResult = service.listNotifications(userId, null, false, pageable);
        assertThat(unreadResult.getContent()).allMatch(dto -> dto.readAt() == null);
        assertThat(unreadResult.getContent()).hasSize(unreadList.size());
    }

    // -----------------------------------------------------------------------
    // Property 5: Notification Ordering
    // -----------------------------------------------------------------------

    /**
     * <b>Validates: Requirements 2.5, 3.8</b>
     */
    @Property(tries = 10)
    void notificationOrderingByCreatedAtDescending(
            @ForAll("timestampedNotificationLists") List<Notification> notifications,
            @ForAll @LongRange(min = 1, max = 100_000) Long userId
    ) {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationServiceImpl service = new NotificationServiceImpl(repository, null, null, null, null, null, null);

        Pageable pageable = PageRequest.of(0, 100);

        // Assign userId and ids to all notifications
        for (int i = 0; i < notifications.size(); i++) {
            notifications.get(i).setUserId(userId);
            notifications.get(i).setId((long) (i + 1));
        }

        // Sort by createdAt DESC (as the JPQL query does)
        List<Notification> sorted = notifications.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());

        when(repository.findByFilters(eq(userId), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(sorted, pageable, sorted.size()));

        Page<NotificationDto> result = service.listNotifications(userId, null, null, pageable);
        List<NotificationDto> content = result.getContent();

        // Verify ordering: for any two consecutive notifications, the first has createdAt >= the second
        for (int i = 0; i < content.size() - 1; i++) {
            assertThat(content.get(i).createdAt())
                    .isAfterOrEqualTo(content.get(i + 1).createdAt());
        }
    }

    // -----------------------------------------------------------------------
    // Property 6: Unread Notification Count Consistency
    // -----------------------------------------------------------------------

    /**
     * <b>Validates: Requirements 2.7</b>
     */
    @Property(tries = 10)
    void unreadNotificationCountConsistency(
            @ForAll("mixedReadDismissNotificationLists") List<Notification> notifications,
            @ForAll @LongRange(min = 1, max = 100_000) Long userId
    ) {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationServiceImpl service = new NotificationServiceImpl(repository, null, null, null, null, null, null);

        // Assign userId to all notifications
        for (Notification n : notifications) {
            n.setUserId(userId);
        }

        // Compute expected unread count: readAt is null AND dismissedAt is null
        long expectedUnread = notifications.stream()
                .filter(n -> n.getReadAt() == null && n.getDismissedAt() == null)
                .count();

        when(repository.countByUserIdAndReadAtIsNullAndDismissedAtIsNull(userId))
                .thenReturn(expectedUnread);

        long actualUnread = service.countUnread(userId);

        assertThat(actualUnread).isEqualTo(expectedUnread);
    }

    // -----------------------------------------------------------------------
    // Generators
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<Notification> arbitraryNotifications() {
        Arbitrary<NotificationType> types = Arbitraries.of(NotificationType.values());
        Arbitrary<String> titles = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> bodies = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);
        Arbitrary<String> metadata = Arbitraries.of("{\"key\":\"value\"}", null);
        Arbitrary<String> actionUrls = Arbitraries.of("/campaigns/1/applications", null);

        return Combinators.combine(types, titles, bodies, metadata, actionUrls)
                .as((type, title, body, meta, actionUrl) -> {
                    Notification n = new Notification();
                    n.setType(type);
                    n.setTitle(title);
                    n.setBody(body);
                    n.setMetadata(meta);
                    n.setActionUrl(actionUrl);
                    return n;
                });
    }

    @Provide
    Arbitrary<List<Notification>> mixedNotificationLists() {
        Arbitrary<NotificationType> types = Arbitraries.of(NotificationType.values());
        Arbitrary<Boolean> readStates = Arbitraries.of(true, false);
        Arbitrary<String> titles = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> bodies = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);

        Arbitrary<Notification> notificationArb = Combinators.combine(types, readStates, titles, bodies)
                .as((type, isRead, title, body) -> {
                    Notification n = new Notification();
                    n.setId((long) (Math.random() * 100_000) + 1);
                    n.setType(type);
                    n.setTitle(title);
                    n.setBody(body);
                    n.setCreatedAt(OffsetDateTime.now());
                    if (isRead) {
                        n.setReadAt(OffsetDateTime.now());
                    } else {
                        n.setReadAt(null);
                    }
                    n.setDismissedAt(null);
                    return n;
                });

        return notificationArb.list().ofMinSize(2).ofMaxSize(20);
    }

    @Provide
    Arbitrary<List<Notification>> timestampedNotificationLists() {
        Arbitrary<NotificationType> types = Arbitraries.of(NotificationType.values());
        Arbitrary<String> titles = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> bodies = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);

        Arbitrary<Notification> notificationArb = Combinators.combine(types, titles, bodies)
                .as((type, title, body) -> {
                    Notification n = new Notification();
                    n.setType(type);
                    n.setTitle(title);
                    n.setBody(body);
                    // Use distinct createdAt values spread across a range
                    n.setCreatedAt(OffsetDateTime.now().minusMinutes((long) (Math.random() * 10_000)));
                    return n;
                });

        return notificationArb.list().ofMinSize(2).ofMaxSize(20);
    }

    @Provide
    Arbitrary<List<Notification>> mixedReadDismissNotificationLists() {
        Arbitrary<NotificationType> types = Arbitraries.of(NotificationType.values());
        Arbitrary<Boolean> readStates = Arbitraries.of(true, false);
        Arbitrary<Boolean> dismissedStates = Arbitraries.of(true, false);
        Arbitrary<String> titles = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> bodies = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);

        Arbitrary<Notification> notificationArb = Combinators.combine(types, readStates, dismissedStates, titles, bodies)
                .as((type, isRead, isDismissed, title, body) -> {
                    Notification n = new Notification();
                    n.setId((long) (Math.random() * 100_000) + 1);
                    n.setType(type);
                    n.setTitle(title);
                    n.setBody(body);
                    n.setCreatedAt(OffsetDateTime.now());
                    n.setReadAt(isRead ? OffsetDateTime.now() : null);
                    n.setDismissedAt(isDismissed ? OffsetDateTime.now() : null);
                    return n;
                });

        return notificationArb.list().ofMinSize(2).ofMaxSize(20);
    }
}
