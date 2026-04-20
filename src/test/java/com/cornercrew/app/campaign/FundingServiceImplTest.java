package com.cornercrew.app.campaign;

import com.cornercrew.app.common.CampaignNotOpenException;
import com.cornercrew.app.common.ContributionExceedsCapException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundingServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private CampaignService campaignService;

    @InjectMocks
    private FundingServiceImpl fundingService;

    private Campaign openCampaign;

    @BeforeEach
    void setUp() {
        openCampaign = buildCampaign(1L, CampaignStatus.OPEN,
                new BigDecimal("1000.00"), BigDecimal.ZERO);
    }

    // --- contribute: successful contribution ---

    @Test
    void contribute_validAmount_persistsContributionAndUpdatesCampaign() {
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCampaign));
        when(contributionRepository.save(any(Contribution.class))).thenAnswer(invocation -> {
            Contribution c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        ContributeRequest req = new ContributeRequest(new BigDecimal("250.00"), ContributionPeriod.MONTH);
        ContributionDto result = fundingService.contribute(1L, 5L, req);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals(1L, result.campaignId());
        assertEquals(5L, result.driverId());
        assertEquals(new BigDecimal("250.00"), result.amount());
        assertEquals(ContributionPeriod.MONTH, result.period());

        // Verify campaign currentAmount was updated
        assertEquals(new BigDecimal("250.00"), openCampaign.getCurrentAmount());

        // Verify checkAndLockIfFunded was called
        verify(campaignService).checkAndLockIfFunded(1L);
    }

    // --- contribute: non-OPEN campaign ---

    @Test
    void contribute_fundedCampaign_throwsCampaignNotOpen() {
        Campaign funded = buildCampaign(2L, CampaignStatus.FUNDED,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        when(campaignRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(funded));

        ContributeRequest req = new ContributeRequest(new BigDecimal("50.00"), ContributionPeriod.DAY);

        assertThrows(CampaignNotOpenException.class,
                () -> fundingService.contribute(2L, 5L, req));

        verify(contributionRepository, never()).save(any());
        verify(campaignService, never()).checkAndLockIfFunded(anyLong());
    }

    @Test
    void contribute_closedCampaign_throwsCampaignNotOpen() {
        Campaign closed = buildCampaign(3L, CampaignStatus.CLOSED,
                new BigDecimal("1000.00"), new BigDecimal("500.00"));
        when(campaignRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(closed));

        ContributeRequest req = new ContributeRequest(new BigDecimal("50.00"), ContributionPeriod.DAY);

        assertThrows(CampaignNotOpenException.class,
                () -> fundingService.contribute(3L, 5L, req));
    }

    // --- contribute: exceeding cap ---

    @Test
    void contribute_amountExceedsCap_throwsContributionExceedsCap() {
        openCampaign.setCurrentAmount(new BigDecimal("900.00"));
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCampaign));

        ContributeRequest req = new ContributeRequest(new BigDecimal("200.00"), ContributionPeriod.WEEK);

        ContributionExceedsCapException ex = assertThrows(ContributionExceedsCapException.class,
                () -> fundingService.contribute(1L, 5L, req));

        assertEquals(new BigDecimal("100.00"), ex.getRemainingCapacity());
        verify(contributionRepository, never()).save(any());
    }

    // --- contribute: exact target triggers funded transition ---

    @Test
    void contribute_exactTarget_callsCheckAndLockIfFunded() {
        openCampaign.setCurrentAmount(new BigDecimal("800.00"));
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCampaign));
        when(contributionRepository.save(any(Contribution.class))).thenAnswer(invocation -> {
            Contribution c = invocation.getArgument(0);
            c.setId(101L);
            return c;
        });
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        ContributeRequest req = new ContributeRequest(new BigDecimal("200.00"), ContributionPeriod.MONTH);
        ContributionDto result = fundingService.contribute(1L, 5L, req);

        assertNotNull(result);
        // currentAmount should now equal targetAmount
        assertEquals(new BigDecimal("1000.00"), openCampaign.getCurrentAmount());

        // checkAndLockIfFunded must be called to trigger the FUNDED transition
        verify(campaignService).checkAndLockIfFunded(1L);
    }

    // --- contribute: campaign not found ---

    @Test
    void contribute_nonExistingCampaign_throwsEntityNotFound() {
        when(campaignRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        ContributeRequest req = new ContributeRequest(new BigDecimal("50.00"), ContributionPeriod.DAY);

        assertThrows(EntityNotFoundException.class,
                () -> fundingService.contribute(99L, 5L, req));
    }

    // --- contribute: amount <= 0 ---

    @Test
    void contribute_zeroAmount_throwsIllegalArgument() {
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCampaign));

        ContributeRequest req = new ContributeRequest(BigDecimal.ZERO, ContributionPeriod.DAY);

        assertThrows(IllegalArgumentException.class,
                () -> fundingService.contribute(1L, 5L, req));
    }

    @Test
    void contribute_negativeAmount_throwsIllegalArgument() {
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCampaign));

        ContributeRequest req = new ContributeRequest(new BigDecimal("-10.00"), ContributionPeriod.DAY);

        assertThrows(IllegalArgumentException.class,
                () -> fundingService.contribute(1L, 5L, req));
    }

    // --- getSummary ---

    @Test
    void getSummary_existingCampaign_returnsCorrectValues() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.OPEN,
                new BigDecimal("1000.00"), new BigDecimal("350.00"));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        FundingSummaryDto summary = fundingService.getSummary(1L);

        assertEquals(new BigDecimal("350.00"), summary.currentTotal());
        assertEquals(new BigDecimal("650.00"), summary.remainingCapacity());
    }

    @Test
    void getSummary_fullyFundedCampaign_returnsZeroRemaining() {
        Campaign campaign = buildCampaign(2L, CampaignStatus.FUNDED,
                new BigDecimal("500.00"), new BigDecimal("500.00"));
        when(campaignRepository.findById(2L)).thenReturn(Optional.of(campaign));

        FundingSummaryDto summary = fundingService.getSummary(2L);

        assertEquals(new BigDecimal("500.00"), summary.currentTotal());
        assertEquals(new BigDecimal("0.00"), summary.remainingCapacity());
    }

    @Test
    void getSummary_nonExistingCampaign_throwsEntityNotFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> fundingService.getSummary(99L));
    }

    // --- helper ---

    private Campaign buildCampaign(Long id, CampaignStatus status,
                                   BigDecimal targetAmount, BigDecimal currentAmount) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setTitle("Test Campaign");
        c.setDescription("A test campaign");
        c.setTargetAmount(targetAmount);
        c.setCurrentAmount(currentAmount);
        c.setStatus(status);
        c.setWindowStart(LocalDate.now().plusDays(1));
        c.setWindowEnd(LocalDate.now().plusDays(30));
        c.setCreatedByAdminId(10L);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }
}
