package com.cornercrew.app.campaign;

import com.cornercrew.app.common.ContributionExceedsCapException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property 1: Funding Cap Invariant
 *
 * For any campaign and any sequence of contribution operations,
 * campaign.currentAmount <= campaign.targetAmount must hold at all times.
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 12.1</b></p>
 */
class FundingCapInvariantPropertyTest {

    @Property(tries = 20)
    void currentAmount_neverExceeds_targetAmount(
            @ForAll("targetAmounts") BigDecimal targetAmount,
            @ForAll("contributionLists") List<BigDecimal> contributions
    ) {
        // --- Set up campaign ---
        Campaign campaign = new Campaign();
        campaign.setId(1L);
        campaign.setTitle("Property Test Campaign");
        campaign.setDescription("Testing funding cap invariant");
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

        FundingServiceImpl fundingService = new FundingServiceImpl(
                campaignRepository, contributionRepository, campaignService);

        // --- Apply each contribution and assert invariant after each ---
        for (BigDecimal amount : contributions) {
            try {
                ContributeRequest req = new ContributeRequest(amount, ContributionPeriod.DAY);
                fundingService.contribute(1L, 5L, req);
            } catch (ContributionExceedsCapException | IllegalArgumentException e) {
                // Expected rejections — invariant should still hold
            }

            // INVARIANT: currentAmount must never exceed targetAmount
            assertThat(campaign.getCurrentAmount())
                    .as("currentAmount (%s) must be <= targetAmount (%s)",
                            campaign.getCurrentAmount(), campaign.getTargetAmount())
                    .isLessThanOrEqualTo(campaign.getTargetAmount());
        }
    }

    @Provide
    Arbitrary<BigDecimal> targetAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("10000.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<List<BigDecimal>> contributionLists() {
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(new BigDecimal("-10.00"), new BigDecimal("5000.00"))
                .ofScale(2);
        return amounts.list().ofMinSize(1).ofMaxSize(20);
    }
}
