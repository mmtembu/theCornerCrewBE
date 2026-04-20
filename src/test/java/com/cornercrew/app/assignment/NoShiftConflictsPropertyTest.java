package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.ShiftConflictException;
import com.cornercrew.app.intersection.IntersectionRepository;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property 5: No Shift Conflicts
 *
 * For any (intersectionId, date, shiftType) triple, at most one active ShiftSlot
 * exists; conflicting assignments are rejected with ShiftConflictException.
 *
 * <p><b>Validates: Requirements 5.4, 5.5, 12.4</b></p>
 */
class NoShiftConflictsPropertyTest {

    private final AtomicLong idSequence = new AtomicLong(1);

    @Property(tries = 20)
    void secondAssignment_isRejected_forSameIntersectionDateShift(
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("shiftDates") List<LocalDate> shiftDates,
            @ForAll("controllerIds") Long controllerId1,
            @ForAll("controllerIds") Long controllerId2
    ) {
        // --- Set up campaign with FUNDED status ---
        Long campaignId = 1L;
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setTitle("Test Campaign");
        campaign.setTargetAmount(new BigDecimal("10000.00"));
        campaign.setCurrentAmount(new BigDecimal("10000.00"));
        campaign.setStatus(CampaignStatus.FUNDED);
        campaign.setWindowStart(LocalDate.now().plusDays(1));
        campaign.setWindowEnd(LocalDate.now().plusDays(30));
        campaign.setCreatedByAdminId(1L);
        campaign.setCreatedAt(OffsetDateTime.now());
        campaign.setLockedAt(OffsetDateTime.now());

        // --- Mock repositories ---
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ShiftSlotRepository shiftSlotRepository = mock(ShiftSlotRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(intersectionRepository.existsById(intersectionId)).thenReturn(true);

        // --- Set up first controller's accepted application ---
        ControllerApplication app1 = new ControllerApplication();
        app1.setId(1L);
        app1.setCampaignId(campaignId);
        app1.setControllerId(controllerId1);
        app1.setStatus(ApplicationStatus.ACCEPTED);
        app1.setAppliedAt(OffsetDateTime.now());

        when(applicationRepository.findByCampaignIdAndControllerId(campaignId, controllerId1))
                .thenReturn(Optional.of(app1));

        // --- Set up second controller's accepted application ---
        ControllerApplication app2 = new ControllerApplication();
        app2.setId(2L);
        app2.setCampaignId(campaignId);
        app2.setControllerId(controllerId2);
        app2.setStatus(ApplicationStatus.ACCEPTED);
        app2.setAppliedAt(OffsetDateTime.now());

        when(applicationRepository.findByCampaignIdAndControllerId(campaignId, controllerId2))
                .thenReturn(Optional.of(app2));

        // --- Mock assignment save ---
        when(assignmentRepository.save(any(Assignment.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    Assignment assignment = inv.getArgument(0);
                    assignment.setId(idSequence.getAndIncrement());
                    assignment.setAssignedAt(OffsetDateTime.now());
                    return assignment;
                });

        // --- Mock shift slot save ---
        when(shiftSlotRepository.save(any(ShiftSlot.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    ShiftSlot slot = inv.getArgument(0);
                    slot.setId(idSequence.getAndIncrement());
                    return slot;
                });

        // --- Initially no conflicts exist ---
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                anyLong(), any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(false);

        AssignmentServiceImpl assignmentService = new AssignmentServiceImpl(
                assignmentRepository,
                shiftSlotRepository,
                campaignRepository,
                applicationRepository,
                intersectionRepository
        );

        // --- First assignment: should succeed ---
        AssignControllerRequest request1 = new AssignControllerRequest(
                controllerId1,
                intersectionId,
                shiftDates,
                new BigDecimal("500.00")
        );

        AssignmentDto result1 = assignmentService.assign(campaignId, request1, 1L);

        assertThat(result1).isNotNull();
        assertThat(result1.intersectionId()).isEqualTo(intersectionId);
        assertThat(result1.status()).isEqualTo(AssignmentStatus.ASSIGNED);

        // --- Now simulate that shift slots exist for the first assignment ---
        // For each date in shiftDates, both MORNING and EVENING slots now exist
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                eq(intersectionId), any(LocalDate.class), any(ShiftType.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    LocalDate date = inv.getArgument(1);
                    return shiftDates.contains(date);
                });

        // --- Second assignment with same intersection and overlapping dates: should throw ---
        AssignControllerRequest request2 = new AssignControllerRequest(
                controllerId2,
                intersectionId,
                shiftDates,
                new BigDecimal("500.00")
        );

        assertThatThrownBy(() -> assignmentService.assign(campaignId, request2, 1L))
                .isInstanceOf(ShiftConflictException.class)
                .hasMessageContaining("Shift conflict detected");

        // --- Verify only one assignment was saved (the first one) ---
        verify(assignmentRepository, times(1)).save(any(Assignment.class));
    }

    @Provide
    Arbitrary<Long> intersectionIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Long> controllerIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<List<LocalDate>> shiftDates() {
        Arbitrary<LocalDate> dates = Arbitraries.of(
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(6),
                LocalDate.now().plusDays(7),
                LocalDate.now().plusDays(8),
                LocalDate.now().plusDays(9)
        );
        return dates.list().ofMinSize(1).ofMaxSize(3).uniqueElements();
    }
}
