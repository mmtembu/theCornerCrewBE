package com.cornercrew.app.campaign;

import com.cornercrew.app.common.InvalidStatusTransitionException;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.intersection.IntersectionStatus;
import com.cornercrew.app.intersection.IntersectionType;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private IntersectionRepository intersectionRepository;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private CreateCampaignRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateCampaignRequest(
                "Test Campaign",
                "A test campaign",
                new BigDecimal("1000.00"),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30)
        );
    }

    // --- createCampaign tests ---

    @Test
    void createCampaign_validRequest_returnsCampaignDto() {
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CampaignDto result = campaignService.createCampaign(validRequest, 10L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Test Campaign", result.title());
        assertEquals(CampaignStatus.OPEN, result.status());
        assertEquals(BigDecimal.ZERO, result.currentAmount());
        assertEquals(new BigDecimal("1000.00"), result.targetAmount());
    }

    @Test
    void createCampaign_windowStartNotBeforeWindowEnd_throwsIllegalArgument() {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Bad", "desc", new BigDecimal("100"),
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(10)
        );

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.createCampaign(req, 10L));
    }

    @Test
    void createCampaign_windowEndInPast_throwsIllegalArgument() {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Bad", "desc", new BigDecimal("100"),
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1)
        );

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.createCampaign(req, 10L));
    }

    @Test
    void createCampaign_targetAmountZero_throwsIllegalArgument() {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Bad", "desc", BigDecimal.ZERO,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30)
        );

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.createCampaign(req, 10L));
    }

    @Test
    void createCampaign_targetAmountNegative_throwsIllegalArgument() {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "Bad", "desc", new BigDecimal("-50"),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30)
        );

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.createCampaign(req, 10L));
    }

    // --- getCampaign tests ---

    @Test
    void getCampaign_existingId_returnsCampaignDto() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.OPEN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        CampaignDto result = campaignService.getCampaign(1L);

        assertEquals(1L, result.id());
        assertEquals("Test Campaign", result.title());
    }

    @Test
    void getCampaign_nonExistingId_throwsEntityNotFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> campaignService.getCampaign(99L));
    }

    // --- listCampaigns tests ---

    @Test
    void listCampaigns_nullStatus_returnsAllCampaigns() {
        Pageable pageable = PageRequest.of(0, 10);
        Campaign c = buildCampaign(1L, CampaignStatus.OPEN);
        when(campaignRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(c)));

        Page<CampaignDto> result = campaignService.listCampaigns(null, pageable);

        assertEquals(1, result.getTotalElements());
        verify(campaignRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listCampaigns_withStatus_filtersbyStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Campaign c = buildCampaign(1L, CampaignStatus.FUNDED);
        when(campaignRepository.findByStatus(CampaignStatus.FUNDED, pageable))
                .thenReturn(new PageImpl<>(List.of(c)));

        Page<CampaignDto> result = campaignService.listCampaigns(CampaignStatus.FUNDED, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(CampaignStatus.FUNDED, result.getContent().get(0).status());
    }

    // --- checkAndLockIfFunded tests ---

    @Test
    void checkAndLockIfFunded_currentAmountMeetsTarget_transitionsToFunded() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.OPEN);
        campaign.setCurrentAmount(new BigDecimal("1000.00"));
        campaign.setTargetAmount(new BigDecimal("1000.00"));
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        campaignService.checkAndLockIfFunded(1L);

        assertEquals(CampaignStatus.FUNDED, campaign.getStatus());
        assertNotNull(campaign.getLockedAt());
    }

    @Test
    void checkAndLockIfFunded_currentAmountBelowTarget_noOp() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.OPEN);
        campaign.setCurrentAmount(new BigDecimal("500.00"));
        campaign.setTargetAmount(new BigDecimal("1000.00"));
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(campaign));

        campaignService.checkAndLockIfFunded(1L);

        assertEquals(CampaignStatus.OPEN, campaign.getStatus());
        assertNull(campaign.getLockedAt());
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void checkAndLockIfFunded_alreadyFunded_noOp() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.FUNDED);
        campaign.setCurrentAmount(new BigDecimal("1000.00"));
        campaign.setTargetAmount(new BigDecimal("1000.00"));
        campaign.setLockedAt(OffsetDateTime.now());
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(campaign));

        campaignService.checkAndLockIfFunded(1L);

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void checkAndLockIfFunded_nonExistingId_throwsEntityNotFound() {
        when(campaignRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> campaignService.checkAndLockIfFunded(99L));
    }

    // --- autoProposeCampaign tests ---

    @Test
    void autoProposeCampaign_validIntersection_createsDraftCampaign() {
        Intersection intersection = new Intersection();
        intersection.setId(5L);
        intersection.setLabel("Oak Ave & Main St");
        intersection.setLatitude(37.8);
        intersection.setLongitude(-122.4);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CONFIRMED);

        when(intersectionRepository.findById(5L)).thenReturn(Optional.of(intersection));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });

        CampaignDto result = campaignService.autoProposeCampaign(5L, 1L);

        assertEquals(10L, result.id());
        assertEquals(CampaignStatus.DRAFT, result.status());
        assertTrue(result.title().contains("Oak Ave & Main St"));
        assertEquals(BigDecimal.ZERO, result.currentAmount());
    }

    @Test
    void autoProposeCampaign_nonExistingIntersection_throwsEntityNotFound() {
        when(intersectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> campaignService.autoProposeCampaign(99L, 1L));
    }

    // --- approveCampaign tests ---

    @Test
    void approveCampaign_draftCampaign_transitionsToOpen() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.DRAFT);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        CampaignDto result = campaignService.approveCampaign(1L);

        assertEquals(CampaignStatus.OPEN, result.status());
    }

    @Test
    void approveCampaign_openCampaign_throwsInvalidStatusTransition() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.OPEN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        assertThrows(InvalidStatusTransitionException.class,
                () -> campaignService.approveCampaign(1L));
    }

    @Test
    void approveCampaign_fundedCampaign_throwsInvalidStatusTransition() {
        Campaign campaign = buildCampaign(1L, CampaignStatus.FUNDED);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        assertThrows(InvalidStatusTransitionException.class,
                () -> campaignService.approveCampaign(1L));
    }

    @Test
    void approveCampaign_nonExistingId_throwsEntityNotFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> campaignService.approveCampaign(99L));
    }

    // --- helper ---

    private Campaign buildCampaign(Long id, CampaignStatus status) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setTitle("Test Campaign");
        c.setDescription("A test campaign");
        c.setTargetAmount(new BigDecimal("1000.00"));
        c.setCurrentAmount(BigDecimal.ZERO);
        c.setStatus(status);
        c.setWindowStart(LocalDate.now().plusDays(1));
        c.setWindowEnd(LocalDate.now().plusDays(30));
        c.setCreatedByAdminId(10L);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }
}
