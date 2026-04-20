package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.ShiftConflictException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ShiftSlotRepository shiftSlotRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private IntersectionRepository intersectionRepository;

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    private Campaign fundedCampaign;
    private ControllerApplication acceptedApplication;
    private Intersection intersection;
    private AssignControllerRequest validRequest;

    @BeforeEach
    void setUp() {
        // Setup funded campaign
        fundedCampaign = new Campaign();
        fundedCampaign.setId(1L);
        fundedCampaign.setTitle("Test Campaign");
        fundedCampaign.setStatus(CampaignStatus.FUNDED);
        fundedCampaign.setTargetAmount(new BigDecimal("1000.00"));
        fundedCampaign.setCurrentAmount(new BigDecimal("1000.00"));
        fundedCampaign.setWindowStart(LocalDate.now().plusDays(1));
        fundedCampaign.setWindowEnd(LocalDate.now().plusDays(30));
        fundedCampaign.setCreatedByAdminId(10L);
        fundedCampaign.setLockedAt(OffsetDateTime.now());

        // Setup accepted application
        acceptedApplication = new ControllerApplication();
        acceptedApplication.setId(1L);
        acceptedApplication.setCampaignId(1L);
        acceptedApplication.setControllerId(100L);
        acceptedApplication.setStatus(ApplicationStatus.ACCEPTED);

        // Setup intersection
        intersection = new Intersection();
        intersection.setId(5L);
        intersection.setLabel("Oak Ave & Main St");
        intersection.setLatitude(37.8);
        intersection.setLongitude(-122.4);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CONFIRMED);

        // Setup valid request
        validRequest = new AssignControllerRequest(
                100L,
                5L,
                List.of(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6)),
                new BigDecimal("500.00")
        );
    }

    // --- assign tests: successful assignment ---

    @Test
    void assign_validRequest_createsAssignmentWithCorrectShiftSlots() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(any(), any(), any()))
                .thenReturn(false);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        AssignmentDto result = assignmentService.assign(1L, validRequest, 10L);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals(1L, result.campaignId());
        assertEquals(100L, result.controllerId());
        assertEquals(5L, result.intersectionId());
        assertEquals(AssignmentStatus.ASSIGNED, result.status());
        assertEquals(new BigDecimal("500.00"), result.agreedPay());
        assertNotNull(result.assignedAt());
        assertNull(result.paidAt());

        // Verify shift slots created: 2 dates * 2 shifts = 4 slots
        verify(shiftSlotRepository, times(4)).save(any(ShiftSlot.class));
    }

    @Test
    void assign_validRequest_createsCorrectNumberOfShiftSlots() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(any(), any(), any()))
                .thenReturn(false);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        // Request with 3 dates
        AssignControllerRequest threeDate = new AssignControllerRequest(
                100L, 5L,
                List.of(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), LocalDate.now().plusDays(7)),
                new BigDecimal("750.00")
        );

        assignmentService.assign(1L, threeDate, 10L);

        // Verify 3 dates * 2 shifts = 6 slots
        verify(shiftSlotRepository, times(6)).save(any(ShiftSlot.class));
    }

    @Test
    void assign_validRequest_createsShiftSlotsWithCorrectTimes() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(any(), any(), any()))
                .thenReturn(false);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        assignmentService.assign(1L, validRequest, 10L);

        ArgumentCaptor<ShiftSlot> slotCaptor = ArgumentCaptor.forClass(ShiftSlot.class);
        verify(shiftSlotRepository, times(4)).save(slotCaptor.capture());

        List<ShiftSlot> slots = slotCaptor.getAllValues();

        // Verify MORNING slots have correct times (07:00-09:00)
        long morningSlots = slots.stream()
                .filter(s -> s.getShiftType() == ShiftType.MORNING)
                .filter(s -> s.getStartTime().getHour() == 7 && s.getStartTime().getMinute() == 0)
                .filter(s -> s.getEndTime().getHour() == 9 && s.getEndTime().getMinute() == 0)
                .count();
        assertEquals(2, morningSlots);

        // Verify EVENING slots have correct times (16:30-18:30)
        long eveningSlots = slots.stream()
                .filter(s -> s.getShiftType() == ShiftType.EVENING)
                .filter(s -> s.getStartTime().getHour() == 16 && s.getStartTime().getMinute() == 30)
                .filter(s -> s.getEndTime().getHour() == 18 && s.getEndTime().getMinute() == 30)
                .count();
        assertEquals(2, eveningSlots);
    }

    // --- assign tests: validation failures ---

    @Test
    void assign_campaignNotFound_throwsEntityNotFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assignmentService.assign(99L, validRequest, 10L));

        verify(assignmentRepository, never()).save(any());
        verify(shiftSlotRepository, never()).save(any());
    }

    @Test
    void assign_campaignNotFunded_throwsIllegalArgument() {
        Campaign openCampaign = new Campaign();
        openCampaign.setId(1L);
        openCampaign.setStatus(CampaignStatus.OPEN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(openCampaign));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertTrue(ex.getMessage().contains("FUNDED"));
        verify(assignmentRepository, never()).save(any());
        verify(shiftSlotRepository, never()).save(any());
    }

    @Test
    void assign_campaignDraft_throwsIllegalArgument() {
        Campaign draftCampaign = new Campaign();
        draftCampaign.setId(1L);
        draftCampaign.setStatus(CampaignStatus.DRAFT);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(draftCampaign));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertTrue(ex.getMessage().contains("FUNDED"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_noApplicationFound_throwsEntityNotFound() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        verify(assignmentRepository, never()).save(any());
        verify(shiftSlotRepository, never()).save(any());
    }

    @Test
    void assign_applicationNotAccepted_throwsIllegalArgument() {
        ControllerApplication pendingApp = new ControllerApplication();
        pendingApp.setId(1L);
        pendingApp.setCampaignId(1L);
        pendingApp.setControllerId(100L);
        pendingApp.setStatus(ApplicationStatus.PENDING);

        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(pendingApp));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertTrue(ex.getMessage().contains("ACCEPTED"));
        verify(assignmentRepository, never()).save(any());
        verify(shiftSlotRepository, never()).save(any());
    }

    @Test
    void assign_applicationRejected_throwsIllegalArgument() {
        ControllerApplication rejectedApp = new ControllerApplication();
        rejectedApp.setId(1L);
        rejectedApp.setCampaignId(1L);
        rejectedApp.setControllerId(100L);
        rejectedApp.setStatus(ApplicationStatus.REJECTED);

        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(rejectedApp));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertTrue(ex.getMessage().contains("ACCEPTED"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_intersectionNotFound_throwsEntityNotFound() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        verify(assignmentRepository, never()).save(any());
        verify(shiftSlotRepository, never()).save(any());
    }

    // --- assign tests: shift conflict detection ---

    @Test
    void assign_morningShiftConflict_throwsShiftConflictException() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);

        LocalDate conflictDate = LocalDate.now().plusDays(5);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, conflictDate, ShiftType.MORNING)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, conflictDate, ShiftType.EVENING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, LocalDate.now().plusDays(6), ShiftType.MORNING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, LocalDate.now().plusDays(6), ShiftType.EVENING)).thenReturn(false);

        ShiftConflictException ex = assertThrows(ShiftConflictException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertTrue(ex.getConflictingSlots().contains(conflictDate + "_MORNING"));
        assertEquals(1, ex.getConflictingSlots().size());
        verify(assignmentRepository, never()).save(any());
        verify(shiftSlotRepository, never()).save(any());
    }

    @Test
    void assign_eveningShiftConflict_throwsShiftConflictException() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);

        LocalDate conflictDate = LocalDate.now().plusDays(6);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, LocalDate.now().plusDays(5), ShiftType.MORNING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, LocalDate.now().plusDays(5), ShiftType.EVENING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, conflictDate, ShiftType.MORNING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, conflictDate, ShiftType.EVENING)).thenReturn(true);

        ShiftConflictException ex = assertThrows(ShiftConflictException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertTrue(ex.getConflictingSlots().contains(conflictDate + "_EVENING"));
        assertEquals(1, ex.getConflictingSlots().size());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_multipleShiftConflicts_throwsShiftConflictExceptionWithAllConflicts() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);

        // Both dates have conflicts
        LocalDate date1 = LocalDate.now().plusDays(5);
        LocalDate date2 = LocalDate.now().plusDays(6);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, date1, ShiftType.MORNING)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, date1, ShiftType.EVENING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, date2, ShiftType.MORNING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, date2, ShiftType.EVENING)).thenReturn(true);

        ShiftConflictException ex = assertThrows(ShiftConflictException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertEquals(2, ex.getConflictingSlots().size());
        assertTrue(ex.getConflictingSlots().contains(date1 + "_MORNING"));
        assertTrue(ex.getConflictingSlots().contains(date2 + "_EVENING"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_bothShiftsConflictOnSameDate_throwsShiftConflictExceptionWithBoth() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(fundedCampaign));
        when(applicationRepository.findByCampaignIdAndControllerId(1L, 100L))
                .thenReturn(Optional.of(acceptedApplication));
        when(intersectionRepository.existsById(5L)).thenReturn(true);

        LocalDate conflictDate = LocalDate.now().plusDays(5);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, conflictDate, ShiftType.MORNING)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, conflictDate, ShiftType.EVENING)).thenReturn(true);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, LocalDate.now().plusDays(6), ShiftType.MORNING)).thenReturn(false);
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                5L, LocalDate.now().plusDays(6), ShiftType.EVENING)).thenReturn(false);

        ShiftConflictException ex = assertThrows(ShiftConflictException.class,
                () -> assignmentService.assign(1L, validRequest, 10L));

        assertEquals(2, ex.getConflictingSlots().size());
        assertTrue(ex.getConflictingSlots().contains(conflictDate + "_MORNING"));
        assertTrue(ex.getConflictingSlots().contains(conflictDate + "_EVENING"));
    }

    // --- listAssignments tests ---

    @Test
    void listAssignments_existingCampaign_returnsAssignmentList() {
        Assignment a1 = buildAssignment(10L, 1L, 100L, 5L);
        Assignment a2 = buildAssignment(11L, 1L, 101L, 6L);
        when(assignmentRepository.findByCampaignId(1L)).thenReturn(List.of(a1, a2));

        List<AssignmentDto> result = assignmentService.listAssignments(1L);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).id());
        assertEquals(11L, result.get(1).id());
    }

    @Test
    void listAssignments_noAssignments_returnsEmptyList() {
        when(assignmentRepository.findByCampaignId(1L)).thenReturn(List.of());

        List<AssignmentDto> result = assignmentService.listAssignments(1L);

        assertTrue(result.isEmpty());
    }

    // --- getAssignment tests ---

    @Test
    void getAssignment_existingId_returnsAssignmentDto() {
        Assignment assignment = buildAssignment(10L, 1L, 100L, 5L);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));

        AssignmentDto result = assignmentService.getAssignment(10L);

        assertEquals(10L, result.id());
        assertEquals(1L, result.campaignId());
        assertEquals(100L, result.controllerId());
        assertEquals(5L, result.intersectionId());
        assertEquals(AssignmentStatus.ASSIGNED, result.status());
    }

    @Test
    void getAssignment_nonExistingId_throwsEntityNotFound() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assignmentService.getAssignment(99L));
    }

    // --- helper methods ---

    private Assignment buildAssignment(Long id, Long campaignId, Long controllerId, Long intersectionId) {
        Assignment a = new Assignment();
        a.setId(id);
        a.setCampaignId(campaignId);
        a.setControllerId(controllerId);
        a.setIntersectionId(intersectionId);
        a.setStatus(AssignmentStatus.ASSIGNED);
        a.setAgreedPay(new BigDecimal("500.00"));
        a.setAssignedAt(OffsetDateTime.now());
        return a;
    }
}
