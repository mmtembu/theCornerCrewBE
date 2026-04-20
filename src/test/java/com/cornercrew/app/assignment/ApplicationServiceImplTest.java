package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.DuplicateApplicationException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    private Campaign openCampaign;
    private Campaign fundedCampaign;

    @BeforeEach
    void setUp() {
        openCampaign = buildCampaign(1L, CampaignStatus.OPEN);
        fundedCampaign = buildCampaign(2L, CampaignStatus.FUNDED);
    }

    // --- apply: successful application ---

    @Test
    void apply_openCampaign_returnsApplicationDto() {
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(openCampaign));
        when(applicationRepository.save(any(ControllerApplication.class))).thenAnswer(invocation -> {
            ControllerApplication app = invocation.getArgument(0);
            app.setId(10L);
            return app;
        });

        ApplyRequest req = new ApplyRequest("I have 5 years of experience");
        ApplicationDto result = applicationService.apply(1L, 100L, req);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals(1L, result.campaignId());
        assertEquals(100L, result.controllerId());
        assertEquals(ApplicationStatus.PENDING, result.status());
        assertEquals("I have 5 years of experience", result.note());
        assertNotNull(result.appliedAt());
    }

    @Test
    void apply_fundedCampaign_returnsApplicationDto() {
        when(applicationRepository.findByCampaignIdAndControllerId(2L, 100L))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(2L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.save(any(ControllerApplication.class))).thenAnswer(invocation -> {
            ControllerApplication app = invocation.getArgument(0);
            app.setId(11L);
            return app;
        });

        ApplicationDto result = applicationService.apply(2L, 100L, new ApplyRequest(null));

        assertNotNull(result);
        assertEquals(ApplicationStatus.PENDING, result.status());
        assertEquals(2L, result.campaignId());
    }

    // --- apply: duplicate application ---

    @Test
    void apply_duplicateApplication_throwsDuplicateApplicationException() {
        ControllerApplication existing = buildApplication(5L, 1L, 100L, ApplicationStatus.PENDING);
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(existing));

        assertThrows(DuplicateApplicationException.class,
                () -> applicationService.apply(1L, 100L, new ApplyRequest("note")));

        verify(campaignRepository, never()).findById(any());
        verify(applicationRepository, never()).save(any());
    }

    // --- apply: invalid campaign status ---

    @Test
    void apply_closedCampaign_throwsIllegalStateException() {
        Campaign closedCampaign = buildCampaign(3L, CampaignStatus.CLOSED);
        when(applicationRepository.findByCampaignIdAndControllerId(3L, 100L))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(3L)).thenReturn(Optional.of(closedCampaign));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> applicationService.apply(3L, 100L, new ApplyRequest("note")));

        assertTrue(ex.getMessage().contains("CLOSED"));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void apply_draftCampaign_throwsIllegalStateException() {
        Campaign draftCampaign = buildCampaign(4L, CampaignStatus.DRAFT);
        when(applicationRepository.findByCampaignIdAndControllerId(4L, 100L))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(4L)).thenReturn(Optional.of(draftCampaign));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> applicationService.apply(4L, 100L, new ApplyRequest("note")));

        assertTrue(ex.getMessage().contains("DRAFT"));
    }

    @Test
    void apply_cancelledCampaign_throwsIllegalStateException() {
        Campaign cancelledCampaign = buildCampaign(5L, CampaignStatus.CANCELLED);
        when(applicationRepository.findByCampaignIdAndControllerId(5L, 100L))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(5L)).thenReturn(Optional.of(cancelledCampaign));

        assertThrows(IllegalStateException.class,
                () -> applicationService.apply(5L, 100L, new ApplyRequest("note")));
    }

    // --- apply: non-existent campaign ---

    @Test
    void apply_nonExistentCampaign_throwsEntityNotFoundException() {
        when(applicationRepository.findByCampaignIdAndControllerId(99L, 100L))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> applicationService.apply(99L, 100L, new ApplyRequest("note")));
    }

    // --- listApplications ---

    @Test
    void listApplications_returnsMappedDtos() {
        ControllerApplication app1 = buildApplication(1L, 1L, 100L, ApplicationStatus.PENDING);
        ControllerApplication app2 = buildApplication(2L, 1L, 200L, ApplicationStatus.ACCEPTED);
        when(applicationRepository.findByCampaignId(1L)).thenReturn(List.of(app1, app2));

        List<ApplicationDto> result = applicationService.listApplications(1L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals(ApplicationStatus.PENDING, result.get(0).status());
        assertEquals(2L, result.get(1).id());
        assertEquals(ApplicationStatus.ACCEPTED, result.get(1).status());
    }

    @Test
    void listApplications_noneFound_returnsEmptyList() {
        when(applicationRepository.findByCampaignId(99L)).thenReturn(List.of());

        List<ApplicationDto> result = applicationService.listApplications(99L);

        assertTrue(result.isEmpty());
    }

    // --- updateStatus ---

    @Test
    void updateStatus_accepted_returnsUpdatedDto() {
        ControllerApplication app = buildApplication(10L, 1L, 100L, ApplicationStatus.PENDING);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(ControllerApplication.class))).thenAnswer(i -> i.getArgument(0));

        ApplicationDto result = applicationService.updateStatus(10L, ApplicationStatus.ACCEPTED);

        assertEquals(ApplicationStatus.ACCEPTED, result.status());
        assertEquals(10L, result.id());
    }

    @Test
    void updateStatus_rejected_returnsUpdatedDto() {
        ControllerApplication app = buildApplication(11L, 1L, 200L, ApplicationStatus.PENDING);
        when(applicationRepository.findById(11L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(ControllerApplication.class))).thenAnswer(i -> i.getArgument(0));

        ApplicationDto result = applicationService.updateStatus(11L, ApplicationStatus.REJECTED);

        assertEquals(ApplicationStatus.REJECTED, result.status());
    }

    @Test
    void updateStatus_nonExistentApplication_throwsEntityNotFoundException() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> applicationService.updateStatus(99L, ApplicationStatus.ACCEPTED));
    }

    // --- helpers ---

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

    private ControllerApplication buildApplication(Long id, Long campaignId, Long controllerId,
                                                    ApplicationStatus status) {
        ControllerApplication app = new ControllerApplication();
        app.setId(id);
        app.setCampaignId(campaignId);
        app.setControllerId(controllerId);
        app.setStatus(status);
        app.setNote("Test note");
        app.setAppliedAt(OffsetDateTime.now());
        return app;
    }
}
