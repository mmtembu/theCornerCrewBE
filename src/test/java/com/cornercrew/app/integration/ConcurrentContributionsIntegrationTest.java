package com.cornercrew.app.integration;

import com.cornercrew.app.campaign.*;
import com.cornercrew.app.geolocation.GeoLocationApiAdapter;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import com.cornercrew.app.user.Role;
import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for concurrent contributions.
 * Verifies row-level locking prevents race conditions and over-funding.
 *
 * <p>Validates: Requirements 3.6, 12.1</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConcurrentContributionsIntegrationTest {

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

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private FundingService fundingService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private TrafficApiAdapter trafficApiAdapter;
    @MockBean private GeoLocationApiAdapter geoLocationApiAdapter;

    private User adminUser;
    private List<User> drivers;

    @BeforeEach
    void setUp() {
        adminUser = createUserIfNotExists("concurrent-admin@test.com", "Admin", Role.ADMIN);
        drivers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            drivers.add(createUserIfNotExists("concurrent-driver-" + i + "@test.com", "Driver " + i, Role.DRIVER));
        }
    }

    @Test
    void concurrentContributions_noOverFunding() throws Exception {
        // Create a campaign with target = 100.00
        Campaign campaign = new Campaign();
        campaign.setTitle("Concurrent Test Campaign");
        campaign.setDescription("Testing concurrent contributions");
        campaign.setTargetAmount(new BigDecimal("100.00"));
        campaign.setCurrentAmount(BigDecimal.ZERO);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setWindowStart(LocalDate.now().plusDays(1));
        campaign.setWindowEnd(LocalDate.now().plusDays(30));
        campaign.setCreatedByAdminId(adminUser.getId());
        campaign.setCreatedAt(OffsetDateTime.now());
        campaign = campaignRepository.save(campaign);

        final Long campaignId = campaign.getId();

        // 10 threads each trying to contribute 20.00 (total 200.00 > target 100.00)
        int threadCount = 10;
        BigDecimal amountPerThread = new BigDecimal("20.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final User driver = drivers.get(i);
            futures.add(executor.submit(() -> {
                latch.await(); // All threads start simultaneously
                try {
                    // Set up security context for this thread so @PreAuthorize passes
                    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                    ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                            driver, null, driver.getAuthorities()));
                    SecurityContextHolder.setContext(ctx);

                    fundingService.contribute(campaignId, driver.getId(),
                            new ContributeRequest(amountPerThread, ContributionPeriod.MONTH));
                    return true; // success
                } catch (Exception e) {
                    return false; // rejected (over-cap or campaign not open)
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }

        latch.countDown(); // Release all threads
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Count successes
        long successCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) successCount++;
        }

        // Verify: exactly 5 contributions of 20.00 should succeed (5 * 20 = 100)
        assertThat(successCount).isEqualTo(5);

        // Verify: campaign currentAmount equals targetAmount exactly
        Campaign result = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(result.getCurrentAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getTargetAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Verify: campaign status is FUNDED
        assertThat(result.getStatus()).isEqualTo(CampaignStatus.FUNDED);

        // Verify: no over-funding
        assertThat(result.getCurrentAmount()).isLessThanOrEqualTo(result.getTargetAmount());
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
