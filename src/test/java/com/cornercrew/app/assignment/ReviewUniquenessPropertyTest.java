package com.cornercrew.app.assignment;

import com.cornercrew.app.common.DuplicateReviewException;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 7: Review Uniqueness
 *
 * For any (assignmentId, driverId) pair, at most one Review exists;
 * a second submission is rejected with DuplicateReviewException.
 *
 * <p><b>Validates: Requirements 6.2, 12.5</b></p>
 */
class ReviewUniquenessPropertyTest {

    private final AtomicLong idSequence = new AtomicLong(1);

    @Property(tries = 20)
    void secondReview_isRejected_forSameAssignmentAndDriver(
            @ForAll("assignmentIds") Long assignmentId,
            @ForAll("driverIds") Long driverId,
            @ForAll("ratings") int rating
    ) {
        // --- Set up assignment with COMPLETED status ---
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setCampaignId(1L);
        assignment.setControllerId(100L);
        assignment.setIntersectionId(200L);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setAgreedPay(new BigDecimal("500.00"));
        assignment.setAssignedAt(OffsetDateTime.now().minusDays(7));

        // --- Mock repositories ---
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);

        // Track the saved review so the second call finds it
        final Review[] savedReview = {null};

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        // First call: no existing review; second call: return the saved one
        when(reviewRepository.findByAssignmentIdAndDriverId(assignmentId, driverId))
                .thenAnswer((InvocationOnMock inv) -> Optional.ofNullable(savedReview[0]));

        when(reviewRepository.save(any(Review.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    Review review = inv.getArgument(0);
                    review.setId(idSequence.getAndIncrement());
                    savedReview[0] = review;
                    return review;
                });

        ReviewServiceImpl reviewService = new ReviewServiceImpl(
                reviewRepository, assignmentRepository);

        // --- First submitReview: should succeed ---
        SubmitReviewRequest request = new SubmitReviewRequest(rating, "Great work!");
        ReviewDto result = reviewService.submitReview(assignmentId, driverId, request);

        assertThat(result).isNotNull();
        assertThat(result.assignmentId()).isEqualTo(assignmentId);
        assertThat(result.driverId()).isEqualTo(driverId);
        assertThat(result.rating()).isEqualTo(rating);

        // --- Second submitReview with same (assignmentId, driverId): should throw ---
        assertThatThrownBy(() -> reviewService.submitReview(assignmentId, driverId, request))
                .isInstanceOf(DuplicateReviewException.class);

        // --- Verify only one save occurred (the first review) ---
        verify(reviewRepository, times(1)).save(any(Review.class));
    }

    @Provide
    Arbitrary<Long> assignmentIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<Long> driverIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<Integer> ratings() {
        return Arbitraries.integers().between(1, 5);
    }
}
