package com.cornercrew.app.assignment;

import com.cornercrew.app.common.AssignmentAlreadyPaidException;
import com.cornercrew.app.common.InvalidStatusTransitionException;
import com.cornercrew.app.common.NoReviewsException;
import com.cornercrew.app.common.RatingBelowThresholdException;
import com.cornercrew.app.config.PayoutProperties;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Implementation of PayoutService for processing controller payouts.
 * Enforces business rules around assignment status, review requirements,
 * and rating thresholds before releasing funds.
 */
@Service
@Transactional
public class PayoutServiceImpl implements PayoutService {

    private final AssignmentRepository assignmentRepository;
    private final ReviewRepository reviewRepository;
    private final PayoutProperties payoutProperties;

    public PayoutServiceImpl(
            AssignmentRepository assignmentRepository,
            ReviewRepository reviewRepository,
            PayoutProperties payoutProperties
    ) {
        this.assignmentRepository = assignmentRepository;
        this.reviewRepository = reviewRepository;
        this.payoutProperties = payoutProperties;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public PayoutResultDto processPayout(Long assignmentId, Long adminId) {
        // Fetch assignment
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Assignment not found with id: " + assignmentId
                ));

        // Validate assignment is not already PAID (check this first for idempotency)
        if (assignment.getPaidAt() != null) {
            throw new AssignmentAlreadyPaidException(assignmentId);
        }

        // Validate assignment status is COMPLETED
        if (assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new InvalidStatusTransitionException(
                    assignment.getStatus().name(),
                    AssignmentStatus.PAID.name()
            );
        }

        // Validate at least one review exists
        long reviewCount = reviewRepository.countByAssignmentId(assignmentId);
        if (reviewCount == 0) {
            throw new NoReviewsException(assignmentId);
        }

        // Calculate average rating
        Double avgRating = reviewRepository.averageRatingByAssignmentId(assignmentId);
        if (avgRating == null) {
            avgRating = 0.0;
        }

        // Validate average rating meets threshold
        double threshold = payoutProperties.getRatingThreshold();
        if (avgRating < threshold) {
            throw new RatingBelowThresholdException(avgRating, threshold);
        }

        // Transition assignment to PAID
        assignment.setStatus(AssignmentStatus.PAID);
        assignment.setPaidAt(OffsetDateTime.now());
        assignmentRepository.save(assignment);

        // Return payout result
        return new PayoutResultDto(
                assignment.getId(),
                assignment.getAgreedPay(),
                avgRating
        );
    }
}
