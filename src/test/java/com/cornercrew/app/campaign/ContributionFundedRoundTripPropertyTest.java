package com.cornercrew.app.campaign;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property 3: Contribution-to-Funded Round Trip
 *
 * For any sequence of valid contributions summing to exactly targetAmount,
 * campaign status ends as FUNDED and currentAmount equals targetAmount.
 *
 * <p><b>Validates: Requirements 2.6, 3.1, 3.7</b></p>
 */
class ContributionFundedRoundTripPropertyTest {

    @Property(tries = 20)
    void contributionsSummingToTarget_resultInFundedStatus(
            @ForAll("targetWithPartitions") TargetAndParts targetAndParts
    ) {
        BigDecimal targetAmount = targetAndParts.targetAmount();
        List<BigDecimal> parts = targetAndParts.parts();

        // --- Set up campaign ---
        Campaign campaign = new Campaign();
        campaign.setId(1L);
        campaign.setTitle("Round Trip Property Test Campaign");
        campaign.setDescription("Testing contribution-to-funded round trip");
        campaign.setTargetAmount(targetAmount);
        campaign.setCurrentAmount(BigDecimal.ZERO);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setWindowStart(LocalDate.now().plusDays(1));
        campaign.setWindowEnd(LocalDate.now().plusDays(30));
        campaign.setCreatedByAdminId(10L);
        campaign.setCreatedAt(OffsetDateTime.now());

        // --- Mock repositories ---
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        ContributionRepository contributionRepository = mock(ContributionRepository.class);
        CampaignService campaignService = mock(CampaignService.class);

        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contributionRepository.save(any(Contribution.class))).thenAnswer(inv -> {
            Contribution c = inv.getArgument(0);
            c.setId(System.nanoTime());
            return c;
        });

        // Mock checkAndLockIfFunded to perform the actual transition logic inline
        doAnswer(inv -> {
            if (campaign.getCurrentAmount().compareTo(campaign.getTargetAmount()) >= 0
                    && campaign.getStatus() == CampaignStatus.OPEN) {
                campaign.setStatus(CampaignStatus.FUNDED);
                campaign.setLockedAt(OffsetDateTime.now());
            }
            return null;
        }).when(campaignService).checkAndLockIfFunded(anyLong());

        FundingServiceImpl fundingService = new FundingServiceImpl(
                campaignRepository, contributionRepository, campaignService);

        // --- Apply each contribution in sequence ---
        for (BigDecimal amount : parts) {
            ContributeRequest req = new ContributeRequest(amount, ContributionPeriod.DAY);
            fundingService.contribute(1L, 5L, req);
        }

        // --- Assert round-trip properties ---
        assertThat(campaign.getStatus())
                .as("Campaign status should be FUNDED after contributions summing to targetAmount")
                .isEqualTo(CampaignStatus.FUNDED);

        assertThat(campaign.getCurrentAmount().compareTo(campaign.getTargetAmount()))
                .as("currentAmount (%s) should equal targetAmount (%s)",
                        campaign.getCurrentAmount(), campaign.getTargetAmount())
                .isEqualTo(0);
    }

    /**
     * Generates a targetAmount and a list of positive parts that sum exactly to it.
     *
     * Strategy:
     * 1. Generate a random targetAmount in [10.00, 10000.00] with scale 2
     * 2. Generate a random number of splits N in [2, 10]
     * 3. Generate N-1 random split points in (0, targetAmount), sort them
     * 4. Compute differences between consecutive split points to get N parts
     * 5. Adjust the last part so the sum is exactly targetAmount (handles rounding)
     * 6. Ensure all parts are > 0 (minimum 0.01)
     */
    @Provide
    Arbitrary<TargetAndParts> targetWithPartitions() {
        Arbitrary<BigDecimal> targets = Arbitraries.bigDecimals()
                .between(new BigDecimal("10.00"), new BigDecimal("10000.00"))
                .ofScale(2);

        Arbitrary<Integer> splitCounts = Arbitraries.integers().between(2, 10);

        return Combinators.combine(targets, splitCounts).flatAs((target, n) -> {
            // Generate N-1 split points in (0, target) as integers in cents
            long targetCents = target.movePointRight(2).longValueExact();
            // We need N-1 distinct split points in [1, targetCents-1]
            // Each part must be at least 1 cent (0.01), so split points must leave room
            if (targetCents < n) {
                // Target too small for this many splits; fall back to 2 parts
                BigDecimal half = target.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
                BigDecimal remainder = target.subtract(half);
                return Arbitraries.just(new TargetAndParts(target, List.of(half, remainder)));
            }

            return Arbitraries.longs()
                    .between(1, targetCents - 1)
                    .list()
                    .ofSize(n - 1)
                    .map(splitPoints -> {
                        // Sort and deduplicate by using a set-like approach
                        List<Long> sorted = new ArrayList<>(splitPoints.stream().distinct().sorted().toList());

                        // If we got fewer unique points than needed, add evenly spaced ones
                        while (sorted.size() < n - 1) {
                            long candidate = targetCents * (sorted.size() + 1) / n;
                            if (candidate > 0 && candidate < targetCents && !sorted.contains(candidate)) {
                                sorted.add(candidate);
                            } else {
                                // Find any unused value
                                for (long v = 1; v < targetCents; v++) {
                                    if (!sorted.contains(v)) {
                                        sorted.add(v);
                                        break;
                                    }
                                }
                            }
                            Collections.sort(sorted);
                        }

                        // Compute parts from split points
                        List<BigDecimal> parts = new ArrayList<>();
                        long prev = 0;
                        for (long sp : sorted) {
                            long partCents = sp - prev;
                            parts.add(BigDecimal.valueOf(partCents, 2));
                            prev = sp;
                        }
                        // Last part: from last split point to targetCents
                        long lastPartCents = targetCents - prev;
                        parts.add(BigDecimal.valueOf(lastPartCents, 2));

                        // Verify all parts are positive (> 0)
                        // If any part is zero due to duplicate split points, merge with neighbor
                        List<BigDecimal> validParts = new ArrayList<>();
                        for (BigDecimal part : parts) {
                            if (part.compareTo(BigDecimal.ZERO) > 0) {
                                validParts.add(part);
                            } else if (!validParts.isEmpty()) {
                                // Merge zero part into previous
                                // (no-op since adding zero doesn't change anything)
                            }
                        }

                        // Adjust last part to ensure exact sum
                        BigDecimal sum = validParts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (sum.compareTo(target) != 0) {
                            BigDecimal diff = target.subtract(sum);
                            int lastIdx = validParts.size() - 1;
                            validParts.set(lastIdx, validParts.get(lastIdx).add(diff));
                        }

                        return new TargetAndParts(target, validParts);
                    });
        });
    }

    record TargetAndParts(BigDecimal targetAmount, List<BigDecimal> parts) {}
}
