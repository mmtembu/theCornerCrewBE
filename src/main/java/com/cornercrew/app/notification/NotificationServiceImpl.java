package com.cornercrew.app.notification;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignIntersection;
import com.cornercrew.app.campaign.CampaignIntersectionRepository;
import com.cornercrew.app.common.NotificationNotFoundException;
import com.cornercrew.app.common.NotificationOwnershipException;
import com.cornercrew.app.commuteprofile.CommuteProfile;
import com.cornercrew.app.commuteprofile.CommuteProfileRepository;
import com.cornercrew.app.config.NotificationProperties;
import com.cornercrew.app.intersection.CongestionSnapshot;
import com.cornercrew.app.intersection.CongestionSnapshotRepository;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.user.Role;
import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int MAX_DELAY_MINUTES = 30;

    private final NotificationRepository notificationRepository;
    private final CommuteProfileRepository commuteProfileRepository;
    private final UserRepository userRepository;
    private final CampaignIntersectionRepository campaignIntersectionRepository;
    private final IntersectionRepository intersectionRepository;
    private final CongestionSnapshotRepository congestionSnapshotRepository;
    private final NotificationProperties notificationProperties;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   CommuteProfileRepository commuteProfileRepository,
                                   UserRepository userRepository,
                                   CampaignIntersectionRepository campaignIntersectionRepository,
                                   IntersectionRepository intersectionRepository,
                                   CongestionSnapshotRepository congestionSnapshotRepository,
                                   NotificationProperties notificationProperties) {
        this.notificationRepository = notificationRepository;
        this.commuteProfileRepository = commuteProfileRepository;
        this.userRepository = userRepository;
        this.campaignIntersectionRepository = campaignIntersectionRepository;
        this.intersectionRepository = intersectionRepository;
        this.congestionSnapshotRepository = congestionSnapshotRepository;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public void onCampaignOpened(Campaign campaign) {
        // Resolve campaign intersections
        List<CampaignIntersection> campaignIntersections =
                campaignIntersectionRepository.findByCampaignId(campaign.getId());

        List<Intersection> intersections = new ArrayList<>();
        for (CampaignIntersection ci : campaignIntersections) {
            intersectionRepository.findById(ci.getIntersectionId())
                    .ifPresent(intersections::add);
        }

        // 1. Driver commute notifications (COMMUTE_IMPACT)
        sendCommuteImpactNotifications(campaign, intersections);

        // 2. Controller job notifications (JOB_AVAILABLE)
        sendJobAvailableNotifications(campaign, intersections);
    }

    private void sendCommuteImpactNotifications(Campaign campaign, List<Intersection> intersections) {
        double radiusKm = notificationProperties.getCommuteRadiusKm();
        List<CommuteProfile> profiles = commuteProfileRepository.findAll();

        for (CommuteProfile profile : profiles) {
            Optional<User> userOpt = userRepository.findById(profile.getDriverId());
            if (userOpt.isEmpty()) {
                continue;
            }
            User user = userOpt.get();
            if (!user.isCommuteNotificationsEnabled()) {
                continue;
            }

            for (Intersection intersection : intersections) {
                if (isWithinProximity(profile, intersection, radiusKm)) {
                    // Get latest congestion snapshot for this intersection
                    Optional<CongestionSnapshot> snapshotOpt =
                            congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(
                                    intersection.getId());

                    double congestionScore = snapshotOpt
                            .map(CongestionSnapshot::getScore)
                            .orElse(0.0);

                    int delayMinutes = (int) Math.round(congestionScore * MAX_DELAY_MINUTES);
                    String formattedDelay = formatDelay(delayMinutes);

                    Notification notification = new Notification();
                    notification.setUserId(user.getId());
                    notification.setType(NotificationType.COMMUTE_IMPACT);
                    notification.setTitle("Campaign near your commute: " + campaign.getTitle());
                    notification.setBody("Traffic at " + intersection.getLabel()
                            + " may add ~" + formattedDelay + " to your commute");
                    notification.setMetadata("{\"campaignId\":" + campaign.getId()
                            + ",\"intersectionId\":" + intersection.getId()
                            + ",\"intersectionLabel\":\"" + escapeJson(intersection.getLabel()) + "\""
                            + ",\"delayMinutes\":" + delayMinutes + "}");
                    notification.setCreatedAt(OffsetDateTime.now());

                    notificationRepository.save(notification);
                    break; // One notification per driver per campaign
                }
            }
        }
    }

    private void sendJobAvailableNotifications(Campaign campaign, List<Intersection> intersections) {
        List<User> controllers = userRepository.findByRole(Role.CONTROLLER);

        String intersectionLabels = intersections.stream()
                .map(Intersection::getLabel)
                .collect(Collectors.joining(", "));

        for (User controller : controllers) {
            if (!controller.isJobNotificationsEnabled()) {
                continue;
            }

            Notification notification = new Notification();
            notification.setUserId(controller.getId());
            notification.setType(NotificationType.JOB_AVAILABLE);
            notification.setTitle("New assignment available: " + campaign.getTitle());
            notification.setBody("Campaign from " + campaign.getWindowStart()
                    + " to " + campaign.getWindowEnd()
                    + ", target: $" + campaign.getTargetAmount()
                    + ". Intersections: " + intersectionLabels);
            notification.setMetadata("{\"campaignId\":" + campaign.getId() + "}");
            notification.setActionUrl("/campaigns/" + campaign.getId() + "/applications");
            notification.setCreatedAt(OffsetDateTime.now());

            notificationRepository.save(notification);
        }
    }

    // --- Haversine cross-track distance proximity check ---

    /**
     * Checks if an intersection is within radiusKm of the commute route
     * (approximated as a straight line from origin to destination).
     * Uses the Haversine-based cross-track distance formula plus endpoint checks.
     */
    static boolean isWithinProximity(CommuteProfile profile, Intersection intersection, double radiusKm) {
        double originLat = profile.getOriginLatitude();
        double originLng = profile.getOriginLongitude();
        double destLat = profile.getDestinationLatitude();
        double destLng = profile.getDestinationLongitude();
        double pointLat = intersection.getLatitude();
        double pointLng = intersection.getLongitude();

        double distOriginToPoint = haversineKm(originLat, originLng, pointLat, pointLng);
        double bearingOriginToDest = bearing(originLat, originLng, destLat, destLng);
        double bearingOriginToPoint = bearing(originLat, originLng, pointLat, pointLng);

        double crossTrackDist = Math.abs(
                Math.asin(Math.sin(distOriginToPoint / EARTH_RADIUS_KM)
                        * Math.sin(bearingOriginToPoint - bearingOriginToDest))
                        * EARTH_RADIUS_KM
        );

        double distToOrigin = distOriginToPoint;
        double distToDest = haversineKm(destLat, destLng, pointLat, pointLng);

        double minDist = Math.min(crossTrackDist, Math.min(distToOrigin, distToDest));

        return minDist <= radiusKm;
    }

    /**
     * Haversine distance in kilometers between two lat/lng points.
     */
    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Initial bearing from point 1 to point 2 in radians.
     */
    static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLng = Math.toRadians(lng2 - lng1);

        double y = Math.sin(dLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);
        return Math.atan2(y, x);
    }

    /**
     * Format delay: if >= 60 minutes → "{hours}h {minutes}m", else → "{minutes} min".
     */
    static String formatDelay(int delayMinutes) {
        if (delayMinutes >= 60) {
            int hours = delayMinutes / 60;
            int minutes = delayMinutes % 60;
            return hours + "h " + minutes + "m";
        }
        return delayMinutes + " min";
    }

    /**
     * Simple JSON string escaping for metadata values.
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // --- Existing methods ---

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> listNotifications(Long userId, NotificationType type, Boolean read, Pageable pageable) {
        return notificationRepository.findByFilters(userId, type, read, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNullAndDismissedAtIsNull(userId);
    }

    @Override
    public NotificationDto markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationOwnershipException(
                    "User " + userId + " does not own notification " + notificationId);
        }

        notification.setReadAt(OffsetDateTime.now());
        Notification saved = notificationRepository.save(notification);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDto getNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationOwnershipException(
                    "User " + userId + " does not own notification " + notificationId);
        }

        return toDto(notification);
    }

    @Override
    public NotificationDto dismiss(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationOwnershipException(
                    "User " + userId + " does not own notification " + notificationId);
        }

        notification.setDismissedAt(OffsetDateTime.now());
        Notification saved = notificationRepository.save(notification);
        return toDto(saved);
    }

    private NotificationDto toDto(Notification entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getTitle(),
                entity.getBody(),
                entity.getMetadata(),
                entity.getActionUrl(),
                entity.getCreatedAt(),
                entity.getReadAt(),
                entity.getDismissedAt()
        );
    }
}
