package com.cornercrew.app.notification;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignIntersection;
import com.cornercrew.app.campaign.CampaignIntersectionRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.commuteprofile.CommuteProfile;
import com.cornercrew.app.commuteprofile.CommuteProfileRepository;
import com.cornercrew.app.config.NotificationProperties;
import com.cornercrew.app.intersection.*;
import com.cornercrew.app.user.Role;
import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 3: Commute Impact Notification Eligibility
 *
 * For any campaign that transitions to OPEN, and for any driver, the driver
 * receives a COMMUTE_IMPACT notification if and only if all three conditions hold:
 * (a) the driver has a stored CommuteProfile,
 * (b) the driver has commuteNotificationsEnabled = true, and
 * (c) at least one of the campaign's intersections falls within the configured
 *     proximity radius of the driver's commute route.
 *
 * <p><b>Validates: Requirements 2.1, 2.4, 11.3</b></p>
 *
 * Property 7: Controller Job Notification Eligibility
 *
 * For any campaign that transitions to OPEN, and for any user with role CONTROLLER,
 * the controller receives a JOB_AVAILABLE notification if and only if the controller
 * has jobNotificationsEnabled = true. The notification should contain the campaign title
 * and an actionUrl matching "/campaigns/{campaignId}/applications".
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 11.4</b></p>
 */
class NotificationEligibilityPropertyTest {

    // -----------------------------------------------------------------------
    // Property 3: Commute Impact Notification Eligibility
    // -----------------------------------------------------------------------

    /**
     * <b>Validates: Requirements 2.1, 2.4, 11.3</b>
     */
    @Property(tries = 10)
    void commuteImpactNotificationEligibility(
            @ForAll("commuteEligibilityScenarios") CommuteScenario scenario
    ) throws Exception {
        // --- Setup mocks ---
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        CommuteProfileRepository commuteProfileRepository = mock(CommuteProfileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CampaignIntersectionRepository campaignIntersectionRepository = mock(CampaignIntersectionRepository.class);
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CongestionSnapshotRepository congestionSnapshotRepository = mock(CongestionSnapshotRepository.class);

        NotificationProperties notificationProperties = new NotificationProperties();
        notificationProperties.setCommuteRadiusKm(2.0);

        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationRepository,
                commuteProfileRepository,
                userRepository,
                campaignIntersectionRepository,
                intersectionRepository,
                congestionSnapshotRepository,
                notificationProperties
        );

        // Build campaign
        Campaign campaign = new Campaign();
        setId(campaign, scenario.campaignId);
        campaign.setTitle(scenario.campaignTitle);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setTargetAmount(new BigDecimal("5000.00"));
        campaign.setWindowStart(LocalDate.now());
        campaign.setWindowEnd(LocalDate.now().plusDays(7));
        campaign.setCreatedByAdminId(1L);

        // Build intersection
        Intersection intersection = new Intersection();
        intersection.setId(scenario.intersectionId);
        intersection.setLabel("Test Intersection");
        intersection.setLatitude(scenario.intersectionLat);
        intersection.setLongitude(scenario.intersectionLng);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CONFIRMED);

        // Build campaign intersection link
        CampaignIntersection ci = new CampaignIntersection();
        ci.setId(1L);
        ci.setCampaignId(scenario.campaignId);
        ci.setIntersectionId(scenario.intersectionId);

        when(campaignIntersectionRepository.findByCampaignId(scenario.campaignId))
                .thenReturn(List.of(ci));
        when(intersectionRepository.findById(scenario.intersectionId))
                .thenReturn(Optional.of(intersection));

        // Build driver user
        User driver = new User();
        setId(driver, scenario.driverId);
        driver.setEmail("driver@test.com");
        driver.setPasswordHash("hash");
        driver.setName("Test Driver");
        driver.setRole(Role.DRIVER);
        driver.setCommuteNotificationsEnabled(scenario.commuteNotificationsEnabled);

        // Setup commute profile (or not)
        if (scenario.hasCommuteProfile) {
            CommuteProfile profile = new CommuteProfile();
            profile.setId(1L);
            profile.setDriverId(scenario.driverId);
            profile.setOriginLatitude(scenario.originLat);
            profile.setOriginLongitude(scenario.originLng);
            profile.setDestinationLatitude(scenario.destLat);
            profile.setDestinationLongitude(scenario.destLng);
            profile.setDepartureStartTime(LocalTime.of(7, 0));
            profile.setDepartureEndTime(LocalTime.of(9, 0));
            profile.setCreatedAt(OffsetDateTime.now());

            when(commuteProfileRepository.findAll()).thenReturn(List.of(profile));
            when(userRepository.findById(scenario.driverId)).thenReturn(Optional.of(driver));
        } else {
            when(commuteProfileRepository.findAll()).thenReturn(Collections.emptyList());
        }

        // No controllers for this test
        when(userRepository.findByRole(Role.CONTROLLER)).thenReturn(Collections.emptyList());

        // Congestion snapshot for delay computation
        CongestionSnapshot snapshot = new CongestionSnapshot();
        snapshot.setId(1L);
        snapshot.setIntersectionId(scenario.intersectionId);
        snapshot.setScore(0.5);
        snapshot.setProvider("test");
        snapshot.setMeasuredAt(OffsetDateTime.now());
        when(congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(scenario.intersectionId))
                .thenReturn(Optional.of(snapshot));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // --- Execute ---
        service.onCampaignOpened(campaign);

        // --- Determine expected result ---
        boolean withinProximity = scenario.hasCommuteProfile &&
                NotificationServiceImpl.isWithinProximity(
                        buildProfile(scenario), intersection, 2.0);

        boolean shouldReceiveNotification = scenario.hasCommuteProfile
                && scenario.commuteNotificationsEnabled
                && withinProximity;

        // --- Verify ---
        if (shouldReceiveNotification) {
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atLeastOnce()).save(captor.capture());

            boolean hasCommuteImpact = captor.getAllValues().stream()
                    .anyMatch(n -> n.getType() == NotificationType.COMMUTE_IMPACT
                            && n.getUserId().equals(scenario.driverId));
            assertThat(hasCommuteImpact)
                    .as("Driver should receive COMMUTE_IMPACT notification")
                    .isTrue();
        } else {
            // Verify no COMMUTE_IMPACT notification was saved for this driver
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atMost(0)).save(captor.capture());
        }
    }

    // -----------------------------------------------------------------------
    // Property 7: Controller Job Notification Eligibility
    // -----------------------------------------------------------------------

    /**
     * <b>Validates: Requirements 3.1, 3.2, 3.3, 11.4</b>
     */
    @Property(tries = 10)
    void controllerJobNotificationEligibility(
            @ForAll("controllerEligibilityScenarios") ControllerScenario scenario
    ) throws Exception {
        // --- Setup mocks ---
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        CommuteProfileRepository commuteProfileRepository = mock(CommuteProfileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CampaignIntersectionRepository campaignIntersectionRepository = mock(CampaignIntersectionRepository.class);
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CongestionSnapshotRepository congestionSnapshotRepository = mock(CongestionSnapshotRepository.class);

        NotificationProperties notificationProperties = new NotificationProperties();
        notificationProperties.setCommuteRadiusKm(2.0);

        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationRepository,
                commuteProfileRepository,
                userRepository,
                campaignIntersectionRepository,
                intersectionRepository,
                congestionSnapshotRepository,
                notificationProperties
        );

        // Build campaign
        Campaign campaign = new Campaign();
        setId(campaign, scenario.campaignId);
        campaign.setTitle(scenario.campaignTitle);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setTargetAmount(scenario.targetAmount);
        campaign.setWindowStart(scenario.windowStart);
        campaign.setWindowEnd(scenario.windowEnd);
        campaign.setCreatedByAdminId(1L);

        // Build intersection
        Intersection intersection = new Intersection();
        intersection.setId(100L);
        intersection.setLabel("Main St & 1st Ave");
        intersection.setLatitude(40.7128);
        intersection.setLongitude(-74.0060);
        intersection.setType(IntersectionType.TRAFFIC_LIGHT);
        intersection.setStatus(IntersectionStatus.CONFIRMED);

        CampaignIntersection ci = new CampaignIntersection();
        ci.setId(1L);
        ci.setCampaignId(scenario.campaignId);
        ci.setIntersectionId(100L);

        when(campaignIntersectionRepository.findByCampaignId(scenario.campaignId))
                .thenReturn(List.of(ci));
        when(intersectionRepository.findById(100L))
                .thenReturn(Optional.of(intersection));

        // No commute profiles (skip driver notifications)
        when(commuteProfileRepository.findAll()).thenReturn(Collections.emptyList());

        // Build controller user
        User controller = new User();
        setId(controller, scenario.controllerId);
        controller.setEmail("controller@test.com");
        controller.setPasswordHash("hash");
        controller.setName("Test Controller");
        controller.setRole(Role.CONTROLLER);
        controller.setJobNotificationsEnabled(scenario.jobNotificationsEnabled);

        when(userRepository.findByRole(Role.CONTROLLER)).thenReturn(List.of(controller));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // --- Execute ---
        service.onCampaignOpened(campaign);

        // --- Verify ---
        if (scenario.jobNotificationsEnabled) {
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atLeastOnce()).save(captor.capture());

            Notification jobNotification = captor.getAllValues().stream()
                    .filter(n -> n.getType() == NotificationType.JOB_AVAILABLE
                            && n.getUserId().equals(scenario.controllerId))
                    .findFirst()
                    .orElse(null);

            assertThat(jobNotification)
                    .as("Controller should receive JOB_AVAILABLE notification")
                    .isNotNull();

            // Verify notification contains campaign title
            assertThat(jobNotification.getTitle())
                    .contains(scenario.campaignTitle);

            // Verify actionUrl matches pattern /campaigns/{id}/applications
            assertThat(jobNotification.getActionUrl())
                    .isEqualTo("/campaigns/" + scenario.campaignId + "/applications");
        } else {
            // No notification should be saved at all (no drivers either)
            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }

    // -----------------------------------------------------------------------
    // Scenario records
    // -----------------------------------------------------------------------

    record CommuteScenario(
            Long campaignId,
            String campaignTitle,
            Long intersectionId,
            double intersectionLat,
            double intersectionLng,
            Long driverId,
            boolean hasCommuteProfile,
            boolean commuteNotificationsEnabled,
            double originLat,
            double originLng,
            double destLat,
            double destLng
    ) {}

    record ControllerScenario(
            Long campaignId,
            String campaignTitle,
            BigDecimal targetAmount,
            LocalDate windowStart,
            LocalDate windowEnd,
            Long controllerId,
            boolean jobNotificationsEnabled
    ) {}

    // -----------------------------------------------------------------------
    // Generators
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<CommuteScenario> commuteEligibilityScenarios() {
        Arbitrary<Boolean> hasProfile = Arbitraries.of(true, false);
        Arbitrary<Boolean> notificationsEnabled = Arbitraries.of(true, false);
        Arbitrary<Boolean> withinProximity = Arbitraries.of(true, false);

        return Combinators.combine(hasProfile, notificationsEnabled, withinProximity)
                .as((profile, enabled, proximity) -> {
                    // Intersection at a fixed location
                    double intLat = 40.7128;
                    double intLng = -74.0060;

                    double originLat, originLng, destLat, destLng;
                    if (proximity) {
                        // Route passes very close to the intersection (within 2km)
                        originLat = 40.710;
                        originLng = -74.010;
                        destLat = 40.715;
                        destLng = -74.000;
                    } else {
                        // Route is far from the intersection (>50km away)
                        originLat = 41.8;
                        originLng = -72.0;
                        destLat = 42.0;
                        destLng = -71.5;
                    }

                    return new CommuteScenario(
                            10L,
                            "Test Campaign",
                            100L,
                            intLat,
                            intLng,
                            200L,
                            profile,
                            enabled,
                            originLat,
                            originLng,
                            destLat,
                            destLng
                    );
                });
    }

    @Provide
    Arbitrary<ControllerScenario> controllerEligibilityScenarios() {
        Arbitrary<Boolean> jobEnabled = Arbitraries.of(true, false);
        Arbitrary<Long> campaignIds = Arbitraries.longs().between(1, 1000);
        Arbitrary<String> titles = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30);
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(new BigDecimal("100"), new BigDecimal("50000"))
                .ofScale(2);

        return Combinators.combine(jobEnabled, campaignIds, titles, amounts)
                .as((enabled, campaignId, title, amount) -> new ControllerScenario(
                        campaignId,
                        title,
                        amount,
                        LocalDate.now(),
                        LocalDate.now().plusDays(7),
                        500L,
                        enabled
                ));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    private static CommuteProfile buildProfile(CommuteScenario scenario) {
        CommuteProfile profile = new CommuteProfile();
        profile.setOriginLatitude(scenario.originLat);
        profile.setOriginLongitude(scenario.originLng);
        profile.setDestinationLatitude(scenario.destLat);
        profile.setDestinationLongitude(scenario.destLng);
        return profile;
    }
}
