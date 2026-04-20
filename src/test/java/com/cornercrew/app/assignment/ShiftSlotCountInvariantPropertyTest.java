package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.intersection.IntersectionRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 6: Shift Slot Count Invariant
 *
 * For any assignment with N shift dates, exactly N * 2 ShiftSlot records are created.
 * This ensures that for each date, both MORNING and EVENING shift slots are created.
 *
 * <p><b>Validates: Requirements 5.5</b></p>
 */
class ShiftSlotCountInvariantPropertyTest {

    private final AtomicLong idSequence = new AtomicLong(1);

    @Property(tries = 20)
    void assignment_createsExactlyTwoSlotsPerDate(
            @ForAll("shiftDateCounts") int dateCount,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("controllerIds") Long controllerId
    ) {
        // --- Generate N random shift dates ---
        List<LocalDate> shiftDates = generateShiftDates(dateCount);
        int expectedSlotCount = dateCount * 2; // N MORNING + N EVENING

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

        // --- Set up controller's accepted application ---
        ControllerApplication application = new ControllerApplication();
        application.setId(1L);
        application.setCampaignId(campaignId);
        application.setControllerId(controllerId);
        application.setStatus(ApplicationStatus.ACCEPTED);
        application.setAppliedAt(OffsetDateTime.now());

        when(applicationRepository.findByCampaignIdAndControllerId(campaignId, controllerId))
                .thenReturn(Optional.of(application));

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

        // --- No existing shift conflicts ---
        when(shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                any(), any(), any()))
                .thenReturn(false);

        AssignmentServiceImpl assignmentService = new AssignmentServiceImpl(
                assignmentRepository,
                shiftSlotRepository,
                campaignRepository,
                applicationRepository,
                intersectionRepository
        );

        // --- Create assignment ---
        AssignControllerRequest request = new AssignControllerRequest(
                controllerId,
                intersectionId,
                shiftDates,
                new BigDecimal("500.00")
        );

        AssignmentDto result = assignmentService.assign(campaignId, request, 1L);

        // --- Verify assignment was created ---
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AssignmentStatus.ASSIGNED);

        // --- Capture all ShiftSlot saves ---
        ArgumentCaptor<ShiftSlot> slotCaptor = ArgumentCaptor.forClass(ShiftSlot.class);
        verify(shiftSlotRepository, times(expectedSlotCount)).save(slotCaptor.capture());

        List<ShiftSlot> savedSlots = slotCaptor.getAllValues();

        // --- Assert exactly N * 2 slots were created ---
        assertThat(savedSlots).hasSize(expectedSlotCount);

        // --- Count MORNING and EVENING slots ---
        long morningCount = savedSlots.stream()
                .filter(slot -> slot.getShiftType() == ShiftType.MORNING)
                .count();
        long eveningCount = savedSlots.stream()
                .filter(slot -> slot.getShiftType() == ShiftType.EVENING)
                .count();

        // --- Assert exactly N MORNING and N EVENING slots ---
        assertThat(morningCount).isEqualTo(dateCount);
        assertThat(eveningCount).isEqualTo(dateCount);

        // --- Verify each date has exactly 2 slots (one MORNING, one EVENING) ---
        for (LocalDate date : shiftDates) {
            long slotsForDate = savedSlots.stream()
                    .filter(slot -> slot.getDate().equals(date))
                    .count();
            assertThat(slotsForDate)
                    .as("Date %s should have exactly 2 slots", date)
                    .isEqualTo(2);

            boolean hasMorning = savedSlots.stream()
                    .anyMatch(slot -> slot.getDate().equals(date) && slot.getShiftType() == ShiftType.MORNING);
            boolean hasEvening = savedSlots.stream()
                    .anyMatch(slot -> slot.getDate().equals(date) && slot.getShiftType() == ShiftType.EVENING);

            assertThat(hasMorning)
                    .as("Date %s should have a MORNING slot", date)
                    .isTrue();
            assertThat(hasEvening)
                    .as("Date %s should have an EVENING slot", date)
                    .isTrue();
        }

        // --- Verify all slots reference the same assignment ---
        Long assignmentId = result.id();
        assertThat(savedSlots)
                .allMatch(slot -> slot.getAssignmentId().equals(assignmentId));

        // --- Verify all slots reference the correct intersection ---
        assertThat(savedSlots)
                .allMatch(slot -> slot.getIntersectionId().equals(intersectionId));
    }

    /**
     * Generate N unique shift dates in the future.
     */
    private List<LocalDate> generateShiftDates(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> LocalDate.now().plusDays(5 + i))
                .toList();
    }

    @Provide
    Arbitrary<Integer> shiftDateCounts() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Long> intersectionIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Long> controllerIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }
}
