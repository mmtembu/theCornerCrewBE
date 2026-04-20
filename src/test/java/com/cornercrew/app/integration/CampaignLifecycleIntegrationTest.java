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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the full campaign lifecycle:
 * create campaign -> contribute -> apply -> assign -> review -> payout.
 *
 * <p>Validates: Requirements 2.1, 3.1, 4.1, 5.1, 6.1, 7.1</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class CampaignLifecycleIntegrationTest {

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
    @Autowired private IntersectionRepository intersectionRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    @MockBean private TrafficApiAdapter trafficApiAdapter;
    @MockBean private GeoLocationApiAdapter geoLocationApiAdapter;

    private String adminToken;
    private String driverToken;
    private String controllerToken;
    private User adminUser;
    private User driverUser;
    private User controllerUser;

    @BeforeEach
    void setUp() {
        // Clean up in correct order to respect FK constraints
        assignmentRepository.deleteAll();
        intersectionRepository.findAll().forEach(i -> {
            // Only delete intersections not referenced by other entities
        });

        adminUser = createUserIfNotExists("admin@test.com", "Admin User", Role.ADMIN);
        driverUser = createUserIfNotExists("driver@test.com", "Driver User", Role.DRIVER);
        controllerUser = createUserIfNotExists("controller@test.com", "Controller User", Role.CONTROLLER);

        adminToken = jwtService.generateAccessToken(adminUser);
        driverToken = jwtService.generateAccessToken(driverUser);
        controllerToken = jwtService.generateAccessToken(controllerUser);
    }

    @Test
    void fullCampaignLifecycle_createContributeApplyAssignReviewPayout() throws Exception {
        // Step 1: Admin creates a campaign
        CreateCampaignRequest createReq = new CreateCampaignRequest(
                "Lifecycle Test Campaign",
                "End-to-end test",
                new BigDecimal("100.00"),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30)
        );

        MvcResult createResult = mockMvc.perform(post("/campaigns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.targetAmount").value(100.00))
                .andExpect(jsonPath("$.currentAmount").value(0))
                .andReturn();

        Long campaignId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Step 2: Driver contributes the full target amount
        ContributeRequest contributeReq = new ContributeRequest(
                new BigDecimal("100.00"),
                ContributionPeriod.MONTH
        );

        mockMvc.perform(post("/campaigns/" + campaignId + "/contributions")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contributeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100.00));

        // Verify campaign is now FUNDED
        mockMvc.perform(get("/campaigns/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FUNDED"))
                .andExpect(jsonPath("$.currentAmount").value(100.00));

        // Step 3: Controller applies to the campaign
        ApplyRequest applyReq = new ApplyRequest("I'm a great controller");

        MvcResult applyResult = mockMvc.perform(post("/campaigns/" + campaignId + "/applications")
                        .header("Authorization", "Bearer " + controllerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Long applicationId = objectMapper.readTree(applyResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Step 4: Admin accepts the application
        mockMvc.perform(put("/campaigns/" + campaignId + "/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ApplicationStatus.ACCEPTED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Step 5: Create an intersection for assignment
        Intersection intersection = new Intersection();
        intersection.setLabel("Test Ave & Main St");
        intersection.setLatitude(37.800);
        intersection.setLongitude(-122.410);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CONFIRMED);
        intersection = intersectionRepository.save(intersection);

        // Step 6: Admin assigns the controller
        AssignControllerRequest assignReq = new AssignControllerRequest(
                controllerUser.getId(),
                intersection.getId(),
                List.of(LocalDate.now().plusDays(5)),
                new BigDecimal("50.00")
        );

        MvcResult assignResult = mockMvc.perform(post("/campaigns/" + campaignId + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn();

        Long assignmentId = objectMapper.readTree(assignResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Step 7: Transition assignment to COMPLETED (direct DB update for test)
        Assignment assignment = assignmentRepository.findById(assignmentId).orElseThrow();
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignmentRepository.save(assignment);

        // Step 8: Driver submits a review
        SubmitReviewRequest reviewReq = new SubmitReviewRequest(4, "Good job!");

        mockMvc.perform(post("/assignments/" + assignmentId + "/reviews")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4));

        // Step 9: Admin triggers payout
        mockMvc.perform(post("/assignments/" + assignmentId + "/payout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.agreedPay").value(50.00))
                .andExpect(jsonPath("$.avgRating").value(4.0));

        // Verify assignment is now PAID
        mockMvc.perform(get("/assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
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
