package com.cornercrew.app.integration;

import com.cornercrew.app.assignment.*;
import com.cornercrew.app.auth.JwtService;
import com.cornercrew.app.campaign.*;
import com.cornercrew.app.geolocation.GeoLocationApiAdapter;
import com.cornercrew.app.intersection.*;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for security and RBAC enforcement.
 * Tests unauthenticated access (401), wrong-role access (403),
 * and correct-role access for key endpoints.
 *
 * <p>Validates: Requirements 1.4, 1.5, 1.6, 13.1, 13.2</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class SecurityRbacIntegrationTest {

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
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @MockBean private TrafficApiAdapter trafficApiAdapter;
    @MockBean private GeoLocationApiAdapter geoLocationApiAdapter;

    private String adminToken;
    private String driverToken;
    private String controllerToken;

    @BeforeEach
    void setUp() {
        User admin = createUserIfNotExists("rbac-admin@test.com", "Admin", Role.ADMIN);
        User driver = createUserIfNotExists("rbac-driver@test.com", "Driver", Role.DRIVER);
        User controller = createUserIfNotExists("rbac-controller@test.com", "Controller", Role.CONTROLLER);

        adminToken = jwtService.generateAccessToken(admin);
        driverToken = jwtService.generateAccessToken(driver);
        controllerToken = jwtService.generateAccessToken(controller);
    }

    // --- Unauthenticated requests return 401 ---

    @Test
    void unauthenticated_getCampaigns_returns401() throws Exception {
        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_postCampaign_returns401() throws Exception {
        mockMvc.perform(post("/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_postContribution_returns401() throws Exception {
        mockMvc.perform(post("/campaigns/1/contributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_postApplication_returns401() throws Exception {
        mockMvc.perform(post("/campaigns/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_intersectionCandidates_returns401() throws Exception {
        mockMvc.perform(get("/intersections/candidates"))
                .andExpect(status().isUnauthorized());
    }

    // --- DRIVER cannot access admin endpoints (403) ---

    @Test
    void driver_createCampaign_returns403() throws Exception {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Test", "desc", new BigDecimal("100.00"),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30)
        );

        mockMvc.perform(post("/campaigns")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void driver_assignController_returns403() throws Exception {
        mockMvc.perform(post("/campaigns/999/assignments")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"controllerId\":1,\"intersectionId\":1,\"shiftDates\":[\"2025-01-01\"],\"agreedPay\":50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void driver_triggerPayout_returns403() throws Exception {
        mockMvc.perform(post("/assignments/999/payout")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void driver_confirmIntersection_returns403() throws Exception {
        mockMvc.perform(post("/intersections/candidates/999/confirm")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    // --- CONTROLLER cannot submit contributions (403) ---

    @Test
    void controller_submitContribution_returns403() throws Exception {
        ContributeRequest req = new ContributeRequest(new BigDecimal("10.00"), ContributionPeriod.MONTH);

        mockMvc.perform(post("/campaigns/999/contributions")
                        .header("Authorization", "Bearer " + controllerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void controller_submitReview_returns403() throws Exception {
        SubmitReviewRequest req = new SubmitReviewRequest(4, "Good");

        mockMvc.perform(post("/assignments/999/reviews")
                        .header("Authorization", "Bearer " + controllerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- ADMIN cannot submit reviews (403) ---

    @Test
    void admin_submitReview_returns403() throws Exception {
        SubmitReviewRequest req = new SubmitReviewRequest(4, "Good");

        mockMvc.perform(post("/assignments/999/reviews")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_submitApplication_returns403() throws Exception {
        mockMvc.perform(post("/campaigns/999/applications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    // --- Correct roles can access their endpoints ---

    @Test
    void admin_canListCampaigns() throws Exception {
        mockMvc.perform(get("/campaigns")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void driver_canListCampaigns() throws Exception {
        mockMvc.perform(get("/campaigns")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
    }

    @Test
    void controller_canListCampaigns() throws Exception {
        mockMvc.perform(get("/campaigns")
                        .header("Authorization", "Bearer " + controllerToken))
                .andExpect(status().isOk());
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
