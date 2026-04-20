package com.cornercrew.app.campaign;

import com.cornercrew.app.common.CampaignNotOpenException;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property 2: Lock Immutability
 *
 * For any campaign that has transitioned to FUNDED, no subsequent contribution
 * shall change currentAmount.
 *
 * <p><b>Validates: Requirements 2.7, 3.4, 12.2</b></p>
 */
class LockImmutabilityPropertyTest {

    @Property(tries = 20)
    void fundedCampaign_rejectsContributions_andCurrentAmountUnchanged(
            @ForAll("fundedAmounts") BigDecimal fundedAmount,
            @ForAll("contributionAmounts") BigDecimal contributionAmount
    ) {
        // --- Set up a FUNDED campaign ---
        Campaign campaign = new Campaign();
        campaign.setId(1L);
        campaign.setTitle("Lock Immutability Test Campaign");
        campaign.setDescription("Testing that FUNDED campaigns reject contributions");
        campaign.setTargetAmount(fundedAmount);
        campaign.setCurrentAmount(fundedAmount); // fully funded
        campaign.setStatus(CampaignStatus.FUNDED);
        campaign.setLockedAt(OffsetDateTime.now());
        campaign.setWindowStart(LocalDate.now().plusDays(1));
        campaign.setWindowEnd(LocalDate.now().plusDays(30));
        campaign.setCreatedByAdminId(10L);
        campaign.setCreatedAt(OffsetDateTime.now());

        BigDecimal originalAmount = campaign.getCurrentAmount();

        // --- Mock repositories ---
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        ContributionRepository contributionRepository = mock(ContributionRepository.class);
        CampaignService campaignService = mock(CampaignService.class);

        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(campaign));

        FundingServiceImpl fundingService = new FundingServiceImpl(
                campaignRepository, contributionRepository, campaignService);

        // --- Attempt contribution — should throw CampaignNotOpenException ---
        ContributeRequest req = new ContributeRequest(contributionAmount, ContributionPeriod.DAY);

        assertThatThrownBy(() -> fundingService.contribute(1L, 5L, req))
                .isInstanceOf(CampaignNotOpenException.class);

        // --- Assert currentAmount has not changed ---
        assertThat(campaign.getCurrentAmount())
                .as("currentAmount must remain %s after rejected contribution to FUNDED campaign",
                        originalAmount)
                .isEqualByComparingTo(originalAmount);

        // --- Assert no Contribution records were saved ---
        verify(contributionRepository, never()).save(any(Contribution.class));
    }

    @Provide
    Arbitrary<BigDecimal> fundedAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("10000.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> contributionAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("5000.00"))
                .ofScale(2);
    }
}
