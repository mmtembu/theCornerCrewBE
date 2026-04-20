package com.cornercrew.app.integration;

import com.cornercrew.app.auth.JwtService;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.geolocation.BoundingBox;
import com.cornercrew.app.geolocation.GeoLocationApiAdapter;
import com.cornercrew.app.geolocation.IntersectionCoordinate;
import com.cornercrew.app.intersection.*;
import com.cornercrew.app.traffic.CongestionData;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import com.cornercrew.app.user.Role;
import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for traffic polling and auto-flagging workflow.
 * Mocks TrafficApiAdapter and GeoLocationApiAdapter to return above-threshold scores,
 * then verifies the full flow: poll -> snapshot -> flag -> confirm -> auto-propose campaign.
 *
 * <p>Validates: Requirements 8.2, 8.3, 8.4, 8.5, 10.2, 10.4</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class TrafficPollingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("cornercrew_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.traffic.polling-interval-ms", () -> "999999999");
        registry.add("app.traffic.congestion-threshold", () -> "0.7");
        registry.add("app.traffic.provider", () -> "TOMTOM");
        registry.add("app.traffic.neighborhood.south-lat", () -> "37.790");
        registry.add("app.traffic.neighborhood.west-lng", () -> "-122.425");
        registry.add("app.traffic.neighborhood.north-lat", () -> "37.810");
        registry.add("app.traffic.neighborhood.east-lng", () -> "-122.400");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private IntersectionRepository intersectionRepository;
    @Autowired private CongestionSnapshotRepository congestionSnapshotRepository;
    @Autowired private CampaignRepository campaignRepository;

    @MockBean private TrafficApiAdapter trafficApiAdapter;
    @MockBean private GeoLocationApiAdapter geoLocationApiAdapter;

    // Use the intersection package's service since that's what TrafficPollingJob uses
    @Autowired private TrafficMonitoringService trafficMonitoringService;
    @Autowired private IntersectionCandidateService intersectionCandidateService;
    @Autowired private CacheManager cacheManager;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // Evict geolocation cache to ensure mocks are called fresh each test
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        User admin = createUserIfNotExists("traffic-admin@test.com", "Admin", Role.ADMIN);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @Test
    void pollAndScore_aboveThreshold_flagsIntersectionAndPersistsSnapshot() throws Exception {
        // Arrange: Create an intersection in CANDIDATE status
        Intersection intersection = new Intersection();
        intersection.setLabel("Traffic Test Ave & Polling St");
        intersection.setLatitude(37.800);
        intersection.setLongitude(-122.410);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CANDIDATE);
        intersection = intersectionRepository.save(intersection);
        final Long intersectionId = intersection.getId();

        // Mock GeoLocationApiAdapter to return our intersection coordinate
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Traffic Test Ave & Polling St");
        when(geoLocationApiAdapter.getIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        // Mock TrafficApiAdapter to return above-threshold score (0.85 > 0.7)
        CongestionData highCongestion = new CongestionData(0.85, "HEAVY", Instant.now());
        when(trafficApiAdapter.getCongestionData(anyDouble(), anyDouble()))
                .thenReturn(highCongestion);

        // Act: Trigger poll
        trafficMonitoringService.pollAndScore();

        // Assert: CongestionSnapshot was persisted
        List<CongestionSnapshot> snapshots = congestionSnapshotRepository.findAll();
        // Filter to our intersection
        List<CongestionSnapshot> ourSnapshots = snapshots.stream()
                .filter(s -> s.getIntersectionId().equals(intersectionId))
                .toList();
        assertThat(ourSnapshots).isNotEmpty();
        CongestionSnapshot snapshot = ourSnapshots.get(ourSnapshots.size() - 1);
        assertThat(snapshot.getScore()).isEqualTo(0.85);
        assertThat(snapshot.getRawLevel()).isEqualTo("HEAVY");
        assertThat(snapshot.getProvider()).isEqualTo("TOMTOM");
        assertThat(snapshot.getMeasuredAt()).isNotNull();
        assertThat(snapshot.getRecordedAt()).isNotNull();

        // Assert: Intersection transitioned to FLAGGED
        Intersection updated = intersectionRepository.findById(intersection.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(IntersectionStatus.FLAGGED);
        assertThat(updated.getCongestionScore()).isEqualTo(0.85);
        assertThat(updated.getLastCheckedAt()).isNotNull();
    }

    @Test
    void adminConfirm_flaggedIntersection_triggersAutoProposedCampaign() throws Exception {
        // Arrange: Create a FLAGGED intersection
        Intersection intersection = new Intersection();
        intersection.setLabel("Confirm Test Ave & Auto St");
        intersection.setLatitude(37.801);
        intersection.setLongitude(-122.411);
        intersection.setType(IntersectionType.TRAFFIC_LIGHT);
        intersection.setStatus(IntersectionStatus.FLAGGED);
        intersection.setCongestionScore(0.9);
        intersection = intersectionRepository.save(intersection);
        final String intersectionLabel = intersection.getLabel();

        long campaignCountBefore = campaignRepository.count();

        // Act: Admin confirms the flagged intersection
        mockMvc.perform(post("/intersections/candidates/" + intersection.getId() + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Assert: Intersection is now CONFIRMED
        Intersection confirmed = intersectionRepository.findById(intersection.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(IntersectionStatus.CONFIRMED);

        // Assert: An auto-proposed DRAFT campaign was created
        long campaignCountAfter = campaignRepository.count();
        assertThat(campaignCountAfter).isGreaterThan(campaignCountBefore);

        // Find the auto-proposed campaign
        var draftCampaigns = campaignRepository.findAll().stream()
                .filter(c -> c.getStatus() == CampaignStatus.DRAFT)
                .filter(c -> c.getTitle().contains(intersectionLabel))
                .toList();
        assertThat(draftCampaigns).isNotEmpty();
    }

    private User createUserIfNotExists(String email, String name, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("password123"));
            user.setName(name);
            user.setRole(role);
            return userRepository.save(user);
        });
    }
}
