package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.DuplicateApplicationException;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property 4: No Double-Application
 *
 * For any (campaignId, controllerId) pair, at most one ControllerApplication
 * record exists; a second attempt is rejected with DuplicateApplicationException.
 *
 * <p><b>Validates: Requirements 4.2, 12.3</b></p>
 */
class NoDoubleApplicationPropertyTest {

    private final AtomicLong idSequence = new AtomicLong(1);

    @Property(tries = 20)
    void secondApplication_isRejected_forSameCampaignAndController(
            @ForAll("campaignIds") Long campaignId,
            @ForAll("controllerIds") Long controllerId,
            @ForAll("campaignStatuses") CampaignStatus campaignStatus
    ) {
        // --- Set up campaign with OPEN or FUNDED status ---
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setTitle("Test Campaign " + campaignId);
        campaign.setTargetAmount(new BigDecimal("1000.00"));
        campaign.setCurrentAmount(BigDecimal.ZERO);
        campaign.setStatus(campaignStatus);
        campaign.setWindowStart(LocalDate.now().plusDays(1));
        campaign.setWindowEnd(LocalDate.now().plusDays(30));
        campaign.setCreatedByAdminId(1L);
        campaign.setCreatedAt(OffsetDateTime.now());

        // --- Mock repositories ---
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);

        // Track the saved application so the second call finds it
        final ControllerApplication[] savedApplication = {null};

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        // First call: no existing application; second call: return the saved one
        when(applicationRepository.findByCampaignIdAndControllerId(campaignId, controllerId))
                .thenAnswer((InvocationOnMock inv) -> Optional.ofNullable(savedApplication[0]));

        when(applicationRepository.save(any(ControllerApplication.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    ControllerApplication app = inv.getArgument(0);
                    app.setId(idSequence.getAndIncrement());
                    savedApplication[0] = app;
                    return app;
                });

        ApplicationServiceImpl applicationService = new ApplicationServiceImpl(
                applicationRepository, campaignRepository);

        // --- First apply: should succeed ---
        ApplyRequest request = new ApplyRequest("I want to help");
        ApplicationDto result = applicationService.apply(campaignId, controllerId, request);

        assertThat(result).isNotNull();
        assertThat(result.campaignId()).isEqualTo(campaignId);
        assertThat(result.controllerId()).isEqualTo(controllerId);
        assertThat(result.status()).isEqualTo(ApplicationStatus.PENDING);

        // --- Second apply with same (campaignId, controllerId): should throw ---
        assertThatThrownBy(() -> applicationService.apply(campaignId, controllerId, request))
                .isInstanceOf(DuplicateApplicationException.class);

        // --- Verify only one save occurred (the first apply) ---
        verify(applicationRepository, times(1)).save(any(ControllerApplication.class));
    }

    @Provide
    Arbitrary<Long> campaignIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<Long> controllerIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<CampaignStatus> campaignStatuses() {
        return Arbitraries.of(CampaignStatus.OPEN, CampaignStatus.FUNDED);
    }
}
