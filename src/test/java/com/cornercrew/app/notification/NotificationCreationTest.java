package com.cornercrew.app.notification;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignIntersection;
import com.cornercrew.app.campaign.CampaignIntersectionRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.commuteprofile.CommuteProfile;
import com.cornercrew.app.commuteprofile.CommuteProfileRepository;
import com.cornercrew.app.config.NotificationProperties;
import com.cornercrew.app.intersection.CongestionSnapshot;
import com.cornercrew.app.intersection.CongestionSnapshotRepository;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.intersection.IntersectionStatus;
import com.cornercrew.app.intersection.IntersectionType;
import com.cornercrew.app.user.Role;
import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCreationTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CommuteProfileRepository commuteProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CampaignIntersectionRepository campaignIntersectionRepository;

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private CongestionSnapshotRepository congestionSnapshotRepository;

    private NotificationProperties notificationProperties;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationProperties = new NotificationProperties();
        notificationProperties.setCommuteRadiusKm(2.0);

        notificationService = new NotificationServiceImpl(
                notificationRepository,
                commuteProfileRepository,
                userRepository,
                campaignIntersectionRepository,
                intersectionRepository,
                congestionSnapshotRepository,
                notificationProperties
        );
    }

    // --- Helper methods ---

    private User createUser(Long id, Role role, String email) throws Exception {
        User user = new User();
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        user.setEmail(email);
        user.setName("Test " + role.name());
        user.setPasswordHash("hashed");
        user.setRole(role);
        return user;
    }

    private Campaign createCampaign(Long id, String title) {
        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setTitle(title);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setTargetAmount(new BigDecimal("5000.00"));
        campaign.setCurrentAmount(BigDecimal.ZERO);
        campaign.setWindowStart(LocalDate.now());
        campaign.setWindowEnd(LocalDate.now().plusDays(7));
        campaign.setCreatedByAdminId(1L);
        return campaign;
    }

    private CampaignIntersection createCampaignIntersection(Long campaignId, Long intersectionId) {
        CampaignIntersection ci = new CampaignIntersection();
        ci.setCampaignId(campaignId);
        ci.setIntersectionId(intersectionId);
        return ci;
    }

    private Intersection createIntersection(Long id, String label, double lat, double lng) {
        Intersection intersection = new Intersection();
        intersection.setId(id);
        intersection.setLabel(label);
        intersection.setLatitude(lat);
        intersection.setLongitude(lng);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CONFIRMED);
        return intersection;
    }

    private CommuteProfile createCommuteProfile(Long driverId, double originLat, double originLng,
                                                 double destLat, double destLng) {
        CommuteProfile profile = new CommuteProfile();
        profile.setDriverId(driverId);
        profile.setOriginLatitude(originLat);
        profile.setOriginLongitude(originLng);
        profile.setDestinationLatitude(destLat);
        profile.setDestinationLongitude(destLng);
        profile.setDepartureStartTime(LocalTime.of(7, 0));
        profile.setDepartureEndTime(LocalTime.of(9, 0));
        profile.setCreatedAt(OffsetDateTime.now());
        return profile;
    }

    // --- Test 1: Driver without commute profile receives no COMMUTE_IMPACT notification ---

    @Test
    void driverWithoutCommuteProfile_receivesNoCommuteImpactNotification() {
        Campaign campaign = createCampaign(10L, "Test Campaign");

        CampaignIntersection ci = createCampaignIntersection(10L, 100L);
        Intersection intersection = createIntersection(100L, "Main & 1st", 40.7128, -74.0060);

        when(campaignIntersectionRepository.findByCampaignId(10L))
                .thenReturn(List.of(ci));
        when(intersectionRepository.findById(100L))
                .thenReturn(Optional.of(intersection));

        // No commute profiles exist
        when(commuteProfileRepository.findAll()).thenReturn(Collections.emptyList());
        // No controllers either
        when(userRepository.findByRole(Role.CONTROLLER)).thenReturn(Collections.emptyList());

        notificationService.onCampaignOpened(campaign);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    // --- Test 2: Controller with jobNotificationsEnabled=false receives no JOB_AVAILABLE ---

    @Test
    void controllerWithJobNotificationsDisabled_receivesNoJobAvailableNotification() throws Exception {
        Campaign campaign = createCampaign(20L, "Job Campaign");

        CampaignIntersection ci = createCampaignIntersection(20L, 200L);
        Intersection intersection = createIntersection(200L, "Oak & Elm", 40.7128, -74.0060);

        when(campaignIntersectionRepository.findByCampaignId(20L))
                .thenReturn(List.of(ci));
        when(intersectionRepository.findById(200L))
                .thenReturn(Optional.of(intersection));

        // No commute profiles
        when(commuteProfileRepository.findAll()).thenReturn(Collections.emptyList());

        // Controller with job notifications disabled
        User controller = createUser(50L, Role.CONTROLLER, "controller@test.com");
        controller.setJobNotificationsEnabled(false);
        when(userRepository.findByRole(Role.CONTROLLER)).thenReturn(List.of(controller));

        notificationService.onCampaignOpened(campaign);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    // --- Test 3: Controller with jobNotificationsEnabled=true receives JOB_AVAILABLE ---

    @Test
    void controllerWithJobNotificationsEnabled_receivesJobAvailableNotification() throws Exception {
        Campaign campaign = createCampaign(30L, "Active Campaign");

        CampaignIntersection ci = createCampaignIntersection(30L, 300L);
        Intersection intersection = createIntersection(300L, "Pine & Cedar", 40.7128, -74.0060);

        when(campaignIntersectionRepository.findByCampaignId(30L))
                .thenReturn(List.of(ci));
        when(intersectionRepository.findById(300L))
                .thenReturn(Optional.of(intersection));

        // No commute profiles
        when(commuteProfileRepository.findAll()).thenReturn(Collections.emptyList());

        // Controller with job notifications enabled
        User controller = createUser(60L, Role.CONTROLLER, "controller@test.com");
        controller.setJobNotificationsEnabled(true);
        when(userRepository.findByRole(Role.CONTROLLER)).thenReturn(List.of(controller));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.onCampaignOpened(campaign);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.JOB_AVAILABLE, saved.getType());
        assertEquals(60L, saved.getUserId());
        assertTrue(saved.getTitle().contains("Active Campaign"));
        assertEquals("/campaigns/30/applications", saved.getActionUrl());
    }

    // --- Test 4: Driver with commute profile and notifications enabled receives COMMUTE_IMPACT ---

    @Test
    void driverWithCommuteProfileNearIntersection_receivesCommuteImpactNotification() throws Exception {
        Campaign campaign = createCampaign(40L, "Nearby Campaign");

        // Intersection at a known location
        double intersectionLat = 40.7128;
        double intersectionLng = -74.0060;

        CampaignIntersection ci = createCampaignIntersection(40L, 400L);
        Intersection intersection = createIntersection(400L, "Broadway & 5th", intersectionLat, intersectionLng);

        when(campaignIntersectionRepository.findByCampaignId(40L))
                .thenReturn(List.of(ci));
        when(intersectionRepository.findById(400L))
                .thenReturn(Optional.of(intersection));

        // Driver with commute profile near the intersection (origin and destination straddle it)
        User driver = createUser(70L, Role.DRIVER, "driver@test.com");
        driver.setCommuteNotificationsEnabled(true);
        when(userRepository.findById(70L)).thenReturn(Optional.of(driver));

        // Origin ~0.5km north, destination ~0.5km south of the intersection
        CommuteProfile profile = createCommuteProfile(70L,
                intersectionLat + 0.005, intersectionLng,   // origin slightly north
                intersectionLat - 0.005, intersectionLng);  // destination slightly south

        when(commuteProfileRepository.findAll()).thenReturn(List.of(profile));

        // No controllers
        when(userRepository.findByRole(Role.CONTROLLER)).thenReturn(Collections.emptyList());

        // Congestion snapshot for the intersection
        CongestionSnapshot snapshot = new CongestionSnapshot();
        snapshot.setIntersectionId(400L);
        snapshot.setScore(0.7);
        when(congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(400L))
                .thenReturn(Optional.of(snapshot));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.onCampaignOpened(campaign);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(NotificationType.COMMUTE_IMPACT, saved.getType());
        assertEquals(70L, saved.getUserId());
        assertTrue(saved.getTitle().contains("Nearby Campaign"));
        assertTrue(saved.getBody().contains("Broadway & 5th"));
    }
}
