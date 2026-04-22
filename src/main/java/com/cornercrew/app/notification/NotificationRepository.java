package com.cornercrew.app.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId" +
           " AND (:type IS NULL OR n.type = :type)" +
           " AND (:read IS NULL OR (:read = true AND n.readAt IS NOT NULL) OR (:read = false AND n.readAt IS NULL))" +
           " ORDER BY n.createdAt DESC")
    Page<Notification> findByFilters(@Param("userId") Long userId,
                                      @Param("type") NotificationType type,
                                      @Param("read") Boolean read,
                                      Pageable pageable);

    long countByUserIdAndReadAtIsNullAndDismissedAtIsNull(Long userId);
}
