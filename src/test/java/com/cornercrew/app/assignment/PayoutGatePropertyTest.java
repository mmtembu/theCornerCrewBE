package com.cornercrew.app.assignment;

import com.cornercrew.app.common.AssignmentAlreadyPaidException;
import com.cornercrew.app.common.InvalidStatusTransitionException;
import com.cornercrew.app.common.NoReviewsException;
import com.cornercrew.app.common.RatingBelowThresholdException;
import com.cornercrew.app.config.PayoutProperties;
import jakarta.persistence.EntityNotFoundException;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 8: Payout Gate
 *
 * Assignment transitions to PAID if and only if status is COMPLETED
 * and AVG(rating) >= threshold.
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 12.6</b></p>
 */
class PayoutGatePropertyTest {

    private final AtomicLong idSequence = new AtomicLong(1);

    @Property(tries = 20)
    void payout_succeeds_whenCompletedAndRatingMeetsThreshold(
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

        // --- Execute payout ---
        PayoutResultDto result = payoutService.processPayout(assignmentId, 1L);

        // --- Verify payout succeeded ---
        assertThat(result).isNotNull();
        assertThat(result.assignmentId()).isEqualTo(assignmentId);
        assertThat(result.agreedPay()).isEqualByComparingTo(assignment.getAgreedPay());
        assertThat(result.avgRating()).isEqualTo(avgRating);

        // --- Verify assignment transitioned to PAID ---
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PAID);
        assertThat(assignment.getPaidAt()).isNotNull();

        verify(assignmentRepository, times(1)).save(assignment);
    }

    @Property(tries = 20)
    void payout_fails_whenRatingBelowThreshold(
            @ForAll("assignmentIds") Long assignmentId,
            @ForAll("ratingsBelowThreshold") List<Integer> ratings,
            @ForAll("thresholds") double threshold
    ) {
        // --- Calculate average rating ---
        double avgRating = ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        // Ensure avgRating < threshold (property precondition)
        Assume.that(avgRating < threshold);

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

        PayoutServiceImpl payoutService = new PayoutServiceImpl(
                assignmentRepository, reviewRepository, payoutProperties);

        // --- Execute payout and expect failure ---
        assertThatThrownBy(() -> payoutService.processPayout(assignmentId, 1L))
                .isInstanceOf(RatingBelowThresholdException.class);

        // --- Verify assignment status unchanged ---
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getPaidAt()).isNull();

        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Property(tries = 20)
    void payout_fails_whenStatusNotCompleted(
            @ForAll("assignmentIds") Long assignmentId,
            @ForAll("nonCompletedStatuses") AssignmentStatus status,
            @ForAll("ratingsAboveThreshold") List<Integer> ratings,
            @ForAll("thresholds") double threshold
    ) {
        // --- Calculate average rating ---
        double avgRating = ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        Assume.that(avgRating >= threshold);

        // --- Set up assignment with non-COMPLETED status ---
        Assignment assignment = createAssignment(assignmentId, status);

        // --- Mock repositories ---
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        PayoutProperties payoutProperties = new PayoutProperties();
        payoutProperties.setRatingThreshold(threshold);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(reviewRepository.countByAssignmentId(assignmentId)).thenReturn((long) ratings.size());
        when(reviewRepository.averageRatingByAssignmentId(assignmentId)).thenReturn(avgRating);

        PayoutServiceImpl payoutService = new PayoutServiceImpl(
                assignmentRepository, reviewRepository, payoutProperties);

        // --- Execute payout and expect failure ---
        assertThatThrownBy(() -> payoutService.processPayout(assignmentId, 1L))
                .isInstanceOf(InvalidStatusTransitionException.class);

        // --- Verify assignment status unchanged ---
        assertThat(assignment.getStatus()).isEqualTo(status);
        assertThat(assignment.getPaidAt()).isNull();

        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Property(tries = 20)
    void payout_fails_whenNoReviews(
            @ForAll("assignmentIds") Long assignmentId,
            @ForAll("thresholds") double threshold
    ) {
        // --- Set up assignment with COMPLETED status ---
        Assignment assignment = createAssignment(assignmentId, AssignmentStatus.COMPLETED);

        // --- Mock repositories ---
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        PayoutProperties payoutProperties = new PayoutProperties();
        payoutProperties.setRatingThreshold(threshold);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(reviewRepository.countByAssignmentId(assignmentId)).thenReturn(0L);

        PayoutServiceImpl payoutService = new PayoutServiceImpl(
                assignmentRepository, reviewRepository, payoutProperties);

        // --- Execute payout and expect failure ---
        assertThatThrownBy(() -> payoutService.processPayout(assignmentId, 1L))
                .isInstanceOf(NoReviewsException.class);

        // --- Verify assignment status unchanged ---
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getPaidAt()).isNull();

        verify(assignmentRepository, never()).save(any(Assignment.class));
    }

    @Property(tries = 20)
    void payout_fails_whenAlreadyPaid(
            @ForAll("assignmentIds") Long assignmentId,
            @ForAll("ratingsAboveThreshold") List<Integer> ratings,
            @ForAll("thresholds") double threshold
    ) {
        // --- Calculate average rating ---
        double avgRating = ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        Assume.that(avgRating >= threshold);

        // --- Set up assignment with COMPLETED status but already paid ---
        Assignment assignment = createAssignment(assignmentId, AssignmentStatus.COMPLETED);
        assignment.setPaidAt(OffsetDateTime.now().minusDays(1)); // Already paid

        // --- Mock repositories ---
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        PayoutProperties payoutProperties = new PayoutProperties();
        payoutProperties.setRatingThreshold(threshold);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        PayoutServiceImpl payoutService = new PayoutServiceImpl(
                assignmentRepository, reviewRepository, payoutProperties);

        // --- Execute payout and expect failure ---
        assertThatThrownBy(() -> payoutService.processPayout(assignmentId, 1L))
                .isInstanceOf(AssignmentAlreadyPaidException.class);

        verify(assignmentRepository, never()).save(any(Assignment.class));
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

    @Provide
    Arbitrary<List<Integer>> ratingsBelowThreshold() {
        // Generate ratings that will likely average < 3.0
        return Arbitraries.integers().between(1, 2)
                .list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<AssignmentStatus> nonCompletedStatuses() {
        return Arbitraries.of(
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.ACTIVE,
                AssignmentStatus.PAID
        );
    }
}
