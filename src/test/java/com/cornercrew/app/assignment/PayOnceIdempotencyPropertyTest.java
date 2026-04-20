package com.cornercrew.app.assignment;

import com.cornercrew.app.common.AssignmentAlreadyPaidException;
import com.cornercrew.app.config.PayoutProperties;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 9: Pay-Once Idempotency
 *
 * For any assignment with status PAID, any subsequent call to processPayout
 * must be rejected with error code ASSIGNMENT_ALREADY_PAID. The assignment
 * status and paidAt timestamp must remain unchanged.
 *
 * <p><b>Validates: Requirements 7.5, 12.7</b></p>
 */
class PayOnceIdempotencyPropertyTest {

    @Property(tries = 20)
    void payout_rejectsSecondAttempt_whenAlreadyPaid(
            @ForAll("assignmentIds") Long assignmentId,
            @ForAll("ratingsAboveThreshold") List<Integer> ratings,
            @ForAll("thresholds") double threshold
    ) {
        // --- Calculate average rating ---
        double avgRating = ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        // Ensure avgRating >= threshold (property precondition)
        Assume.that(avgRating >= threshold);

        // --- Set up assignment with COMPLETED status ---
        Assignment assignment = createAssignment(assignmentId, AssignmentStatus.COMPLETED);

        // --- Mock repositories ---
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        PayoutProperties payoutProperties = new PayoutProperties();
        payoutProperties.setRatingThreshold(threshold);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(reviewRepository.countByAssignmentId(assignmentId)).thenReturn((long) ratings.size());
        when(reviewRepository.averageRatingByAssignmentId(assignmentId)).thenReturn(avgRating);
        when(assignmentRepository.save(any(Assignment.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        PayoutServiceImpl payoutService = new PayoutServiceImpl(
                assignmentRepository, reviewRepository, payoutProperties);

        // --- Execute first payout (should succeed) ---
        PayoutResultDto firstResult = payoutService.processPayout(assignmentId, 1L);

        // --- Verify first payout succeeded ---
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.assignmentId()).isEqualTo(assignmentId);
        assertThat(firstResult.agreedPay()).isEqualByComparingTo(assignment.getAgreedPay());
        assertThat(firstResult.avgRating()).isEqualTo(avgRating);

        // --- Verify assignment transitioned to PAID ---
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PAID);
        assertThat(assignment.getPaidAt()).isNotNull();
        
        // Capture the paidAt timestamp after first payout
        OffsetDateTime firstPaidAt = assignment.getPaidAt();

        // --- Verify first save was called ---
        verify(assignmentRepository, times(1)).save(assignment);

        // --- Execute second payout attempt (should fail) ---
        assertThatThrownBy(() -> payoutService.processPayout(assignmentId, 1L))
                .isInstanceOf(AssignmentAlreadyPaidException.class)
                .hasMessageContaining(assignmentId.toString());

        // --- Verify assignment state unchanged after second attempt ---
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PAID);
        assertThat(assignment.getPaidAt()).isEqualTo(firstPaidAt);

        // --- Verify no additional save() calls after the exception ---
        verify(assignmentRepository, times(1)).save(assignment);
    }

    // --- Helper Methods ---

    private Assignment createAssignment(Long assignmentId, AssignmentStatus status) {
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setCampaignId(1L);
        assignment.setControllerId(100L);
        assignment.setIntersectionId(200L);
        assignment.setStatus(status);
        assignment.setAgreedPay(new BigDecimal("500.00"));
        assignment.setAssignedAt(OffsetDateTime.now().minusDays(7));
        return assignment;
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<Long> assignmentIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<Double> thresholds() {
        return Arbitraries.doubles().between(1.0, 5.0);
    }

    @Provide
    Arbitrary<List<Integer>> ratingsAboveThreshold() {
        // Generate ratings that will likely average >= 3.0
        return Arbitraries.integers().between(3, 5)
                .list().ofMinSize(1).ofMaxSize(10);
    }
}
