package com.cornercrew.app.assignment;

import com.cornercrew.app.common.DuplicateReviewException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private Assignment completedAssignment;
    private SubmitReviewRequest validRequest;

    @BeforeEach
    void setUp() {
        // Setup completed assignment
        completedAssignment = new Assignment();
        completedAssignment.setId(1L);
        completedAssignment.setCampaignId(10L);
        completedAssignment.setControllerId(100L);
        completedAssignment.setIntersectionId(5L);
        completedAssignment.setStatus(AssignmentStatus.COMPLETED);

        // Setup valid review request
        validRequest = new SubmitReviewRequest(4, "Great job!");
    }

    // --- submitReview tests: successful submission ---

    @Test
    void submitReview_validRequest_createsAndReturnsReview() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReviewDto result = reviewService.submitReview(1L, 200L, validRequest);

        assertNotNull(result);
        assertEquals(50L, result.id());
        assertEquals(1L, result.assignmentId());
        assertEquals(200L, result.driverId());
        assertEquals(4, result.rating());
        assertEquals("Great job!", result.comment());
        assertNotNull(result.reviewedAt());

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void submitReview_validRequest_setsCorrectReviewFields() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        reviewService.submitReview(1L, 200L, validRequest);

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());

        Review savedReview = reviewCaptor.getValue();
        assertEquals(1L, savedReview.getAssignmentId());
        assertEquals(200L, savedReview.getDriverId());
        assertEquals(4, savedReview.getRating());
        assertEquals("Great job!", savedReview.getComment());
        assertNotNull(savedReview.getReviewedAt());
    }

    @Test
    void submitReview_minRating_acceptsRatingOfOne() {
        SubmitReviewRequest minRatingRequest = new SubmitReviewRequest(1, "Needs improvement");
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReviewDto result = reviewService.submitReview(1L, 200L, minRatingRequest);

        assertEquals(1, result.rating());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void submitReview_maxRating_acceptsRatingOfFive() {
        SubmitReviewRequest maxRatingRequest = new SubmitReviewRequest(5, "Excellent!");
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReviewDto result = reviewService.submitReview(1L, 200L, maxRatingRequest);

        assertEquals(5, result.rating());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void submitReview_nullComment_acceptsNullComment() {
        SubmitReviewRequest noCommentRequest = new SubmitReviewRequest(3, null);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReviewDto result = reviewService.submitReview(1L, 200L, noCommentRequest);

        assertNull(result.comment());
        verify(reviewRepository).save(any(Review.class));
    }

    // --- submitReview tests: validation failures ---

    @Test
    void submitReview_assignmentNotFound_throwsEntityNotFound() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> reviewService.submitReview(99L, 200L, validRequest));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReview_assignmentNotCompleted_throwsIllegalArgument() {
        Assignment assignedAssignment = new Assignment();
        assignedAssignment.setId(1L);
        assignedAssignment.setStatus(AssignmentStatus.ASSIGNED);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignedAssignment));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reviewService.submitReview(1L, 200L, validRequest));

        assertTrue(ex.getMessage().contains("COMPLETED"));
        assertTrue(ex.getMessage().contains("ASSIGNED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReview_assignmentActive_throwsIllegalArgument() {
        Assignment activeAssignment = new Assignment();
        activeAssignment.setId(1L);
        activeAssignment.setStatus(AssignmentStatus.ACTIVE);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(activeAssignment));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reviewService.submitReview(1L, 200L, validRequest));

        assertTrue(ex.getMessage().contains("COMPLETED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReview_assignmentPaid_throwsIllegalArgument() {
        Assignment paidAssignment = new Assignment();
        paidAssignment.setId(1L);
        paidAssignment.setStatus(AssignmentStatus.PAID);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(paidAssignment));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reviewService.submitReview(1L, 200L, validRequest));

        assertTrue(ex.getMessage().contains("COMPLETED"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReview_duplicateReview_throwsDuplicateReviewException() {
        Review existingReview = new Review();
        existingReview.setId(40L);
        existingReview.setAssignmentId(1L);
        existingReview.setDriverId(200L);
        existingReview.setRating(5);

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L))
                .thenReturn(Optional.of(existingReview));

        assertThrows(DuplicateReviewException.class,
                () -> reviewService.submitReview(1L, 200L, validRequest));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReview_ratingBelowOne_throwsIllegalArgument() {
        SubmitReviewRequest invalidRequest = new SubmitReviewRequest(0, "Invalid");
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reviewService.submitReview(1L, 200L, invalidRequest));

        assertTrue(ex.getMessage().contains("between 1 and 5"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReview_ratingAboveFive_throwsIllegalArgument() {
        SubmitReviewRequest invalidRequest = new SubmitReviewRequest(6, "Invalid");
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.findByAssignmentIdAndDriverId(1L, 200L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reviewService.submitReview(1L, 200L, invalidRequest));

        assertTrue(ex.getMessage().contains("between 1 and 5"));
        verify(reviewRepository, never()).save(any());
    }

    // --- getSummary tests ---

    @Test
    void getSummary_assignmentWithReviews_returnsCorrectSummary() {
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.countByAssignmentId(1L)).thenReturn(3L);
        when(reviewRepository.averageRatingByAssignmentId(1L)).thenReturn(4.0);

        ReviewSummaryDto result = reviewService.getSummary(1L);

        assertEquals(4.0, result.avgRating());
        assertEquals(3L, result.reviewCount());
    }

    @Test
    void getSummary_assignmentWithNoReviews_returnsZeroAvgAndCount() {
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.countByAssignmentId(1L)).thenReturn(0L);
        when(reviewRepository.averageRatingByAssignmentId(1L)).thenReturn(0.0);

        ReviewSummaryDto result = reviewService.getSummary(1L);

        assertEquals(0.0, result.avgRating());
        assertEquals(0L, result.reviewCount());
    }

    @Test
    void getSummary_assignmentNotFound_throwsEntityNotFound() {
        when(assignmentRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> reviewService.getSummary(99L));

        verify(reviewRepository, never()).countByAssignmentId(any());
        verify(reviewRepository, never()).averageRatingByAssignmentId(any());
    }

    @Test
    void getSummary_singleReview_returnsCorrectAverage() {
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.countByAssignmentId(1L)).thenReturn(1L);
        when(reviewRepository.averageRatingByAssignmentId(1L)).thenReturn(5.0);

        ReviewSummaryDto result = reviewService.getSummary(1L);

        assertEquals(5.0, result.avgRating());
        assertEquals(1L, result.reviewCount());
    }

    @Test
    void getSummary_multipleReviews_returnsCorrectAverage() {
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.countByAssignmentId(1L)).thenReturn(5L);
        when(reviewRepository.averageRatingByAssignmentId(1L)).thenReturn(3.6);

        ReviewSummaryDto result = reviewService.getSummary(1L);

        assertEquals(3.6, result.avgRating());
        assertEquals(5L, result.reviewCount());
    }
}
