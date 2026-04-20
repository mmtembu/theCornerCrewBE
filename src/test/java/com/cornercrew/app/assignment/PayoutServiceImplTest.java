package com.cornercrew.app.assignment;

import com.cornercrew.app.common.AssignmentAlreadyPaidException;
import com.cornercrew.app.common.InvalidStatusTransitionException;
import com.cornercrew.app.common.NoReviewsException;
import com.cornercrew.app.common.RatingBelowThresholdException;
import com.cornercrew.app.config.PayoutProperties;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private PayoutProperties payoutProperties;

    @InjectMocks
    private PayoutServiceImpl payoutService;

    private Assignment completedAssignment;
    private static final Long ASSIGNMENT_ID = 1L;
    private static final Long ADMIN_ID = 100L;
    private static final BigDecimal AGREED_PAY = new BigDecimal("150.00");
    private static final double DEFAULT_THRESHOLD = 3.0;

    @BeforeEach
    void setUp() {
        // Setup completed assignment
        completedAssignment = new Assignment();
        completedAssignment.setId(ASSIGNMENT_ID);
        completedAssignment.setCampaignId(10L);
        completedAssignment.setControllerId(200L);
        completedAssignment.setIntersectionId(5L);
        completedAssignment.setStatus(AssignmentStatus.COMPLETED);
        completedAssignment.setAgreedPay(AGREED_PAY);
        completedAssignment.setAssignedAt(OffsetDateTime.now().minusDays(1));
        completedAssignment.setPaidAt(null);
    }

    // --- processPayout tests: successful payout ---

    @Test
    void processPayout_validRequest_processesPayoutSuccessfully() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(3L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(4.5);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutResultDto result = payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        assertNotNull(result);
        assertEquals(ASSIGNMENT_ID, result.assignmentId());
        assertEquals(AGREED_PAY, result.agreedPay());
        assertEquals(4.5, result.avgRating());

        verify(assignmentRepository).save(any(Assignment.class));
    }

    @Test
    void processPayout_validRequest_transitionsAssignmentToPaid() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(2L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(4.0);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        ArgumentCaptor<Assignment> assignmentCaptor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());

        Assignment savedAssignment = assignmentCaptor.getValue();
        assertEquals(AssignmentStatus.PAID, savedAssignment.getStatus());
        assertNotNull(savedAssignment.getPaidAt());
    }

    @Test
    void processPayout_validRequest_setsPaidAtTimestamp() {
        OffsetDateTime beforePayout = OffsetDateTime.now();
        
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(1L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(5.0);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        ArgumentCaptor<Assignment> assignmentCaptor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());

        Assignment savedAssignment = assignmentCaptor.getValue();
        assertNotNull(savedAssignment.getPaidAt());
        assertTrue(savedAssignment.getPaidAt().isAfter(beforePayout) || 
                   savedAssignment.getPaidAt().isEqual(beforePayout));
    }

    @Test
    void processPayout_ratingExactlyAtThreshold_processesSuccessfully() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(2L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(3.0);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutResultDto result = payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        assertEquals(3.0, result.avgRating());
        verify(assignmentRepository).save(any(Assignment.class));
    }

    @Test
    void processPayout_highRating_processesSuccessfully() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(5L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(4.8);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutResultDto result = payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        assertEquals(4.8, result.avgRating());
        verify(assignmentRepository).save(any(Assignment.class));
    }

    @Test
    void processPayout_singleReview_processesSuccessfully() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(1L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(5.0);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutResultDto result = payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        assertEquals(5.0, result.avgRating());
        verify(assignmentRepository).save(any(Assignment.class));
    }

    @Test
    void processPayout_validRequest_returnsCorrectAgreedPay() {
        BigDecimal customPay = new BigDecimal("250.75");
        completedAssignment.setAgreedPay(customPay);

        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(2L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(4.0);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutResultDto result = payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        assertEquals(customPay, result.agreedPay());
    }

    // --- processPayout tests: assignment not found ---

    @Test
    void processPayout_assignmentNotFound_throwsEntityNotFoundException() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> payoutService.processPayout(99L, ADMIN_ID));

        verify(assignmentRepository, never()).save(any());
    }

    // --- processPayout tests: invalid status ---

    @Test
    void processPayout_assignmentNotCompleted_throwsInvalidStatusTransitionException() {
        Assignment assignedAssignment = new Assignment();
        assignedAssignment.setId(ASSIGNMENT_ID);
        assignedAssignment.setStatus(AssignmentStatus.ASSIGNED);
        assignedAssignment.setPaidAt(null);

        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignedAssignment));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertTrue(ex.getMessage().contains("ASSIGNED"));
        assertTrue(ex.getMessage().contains("PAID"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void processPayout_assignmentActive_throwsInvalidStatusTransitionException() {
        Assignment activeAssignment = new Assignment();
        activeAssignment.setId(ASSIGNMENT_ID);
        activeAssignment.setStatus(AssignmentStatus.ACTIVE);
        activeAssignment.setPaidAt(null);

        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(activeAssignment));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertTrue(ex.getMessage().contains("ACTIVE"));
        assertTrue(ex.getMessage().contains("PAID"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void processPayout_assignmentPaid_throwsAssignmentAlreadyPaidException() {
        Assignment paidAssignment = new Assignment();
        paidAssignment.setId(ASSIGNMENT_ID);
        paidAssignment.setStatus(AssignmentStatus.PAID);
        paidAssignment.setPaidAt(OffsetDateTime.now());

        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(paidAssignment));

        // Assignment with PAID status and paidAt set will throw AssignmentAlreadyPaidException
        assertThrows(AssignmentAlreadyPaidException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        verify(assignmentRepository, never()).save(any());
    }

    // --- processPayout tests: already paid ---

    @Test
    void processPayout_assignmentAlreadyPaid_throwsAssignmentAlreadyPaidException() {
        completedAssignment.setPaidAt(OffsetDateTime.now().minusHours(1));

        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));

        AssignmentAlreadyPaidException ex = assertThrows(AssignmentAlreadyPaidException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(ASSIGNMENT_ID, ex.getAssignmentId());
        assertTrue(ex.getMessage().contains("already been paid"));
        verify(assignmentRepository, never()).save(any());
    }

    // --- processPayout tests: no reviews ---

    @Test
    void processPayout_noReviews_throwsNoReviewsException() {
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(0L);

        NoReviewsException ex = assertThrows(NoReviewsException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(ASSIGNMENT_ID, ex.getAssignmentId());
        assertTrue(ex.getMessage().contains("has no reviews"));
        verify(assignmentRepository, never()).save(any());
    }

    // --- processPayout tests: rating below threshold ---

    @Test
    void processPayout_ratingBelowThreshold_throwsRatingBelowThresholdException() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(3L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(2.5);

        RatingBelowThresholdException ex = assertThrows(RatingBelowThresholdException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(2.5, ex.getAvgRating());
        assertEquals(DEFAULT_THRESHOLD, ex.getThreshold());
        assertTrue(ex.getMessage().contains("2.5"));
        assertTrue(ex.getMessage().contains("3.0"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void processPayout_ratingJustBelowThreshold_throwsRatingBelowThresholdException() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(2L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(2.99);

        RatingBelowThresholdException ex = assertThrows(RatingBelowThresholdException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(2.99, ex.getAvgRating());
        assertEquals(DEFAULT_THRESHOLD, ex.getThreshold());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void processPayout_veryLowRating_throwsRatingBelowThresholdException() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(5L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(1.0);

        RatingBelowThresholdException ex = assertThrows(RatingBelowThresholdException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(1.0, ex.getAvgRating());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void processPayout_nullAverageRating_treatsAsZeroAndThrowsException() {
        when(payoutProperties.getRatingThreshold()).thenReturn(DEFAULT_THRESHOLD);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(1L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(null);

        RatingBelowThresholdException ex = assertThrows(RatingBelowThresholdException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(0.0, ex.getAvgRating());
        verify(assignmentRepository, never()).save(any());
    }

    // --- processPayout tests: custom threshold ---

    @Test
    void processPayout_customThreshold_respectsConfiguredValue() {
        when(payoutProperties.getRatingThreshold()).thenReturn(4.0);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(2L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(4.5);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutResultDto result = payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID);

        assertEquals(4.5, result.avgRating());
        verify(assignmentRepository).save(any(Assignment.class));
    }

    @Test
    void processPayout_customThreshold_rejectsRatingBelowCustomThreshold() {
        when(payoutProperties.getRatingThreshold()).thenReturn(4.5);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));
        when(reviewRepository.countByAssignmentId(ASSIGNMENT_ID)).thenReturn(3L);
        when(reviewRepository.averageRatingByAssignmentId(ASSIGNMENT_ID)).thenReturn(4.0);

        RatingBelowThresholdException ex = assertThrows(RatingBelowThresholdException.class,
                () -> payoutService.processPayout(ASSIGNMENT_ID, ADMIN_ID));

        assertEquals(4.0, ex.getAvgRating());
        assertEquals(4.5, ex.getThreshold());
        verify(assignmentRepository, never()).save(any());
    }
}
