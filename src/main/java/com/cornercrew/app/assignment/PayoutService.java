package com.cornercrew.app.assignment;

/**
 * Service for processing controller payouts after assignment completion.
 * Validates review ratings and transitions assignments to PAID status.
 */
public interface PayoutService {

    /**
     * Processes payout for a completed assignment.
     * Validates that the assignment is COMPLETED, not already PAID,
     * has at least one review, and the average rating meets the threshold.
     *
     * @param assignmentId the ID of the assignment to pay out
     * @param adminId the ID of the admin processing the payout
     * @return PayoutResultDto containing agreed pay and average rating
     * @throws com.cornercrew.app.common.AssignmentAlreadyPaidException if assignment is already paid
     * @throws com.cornercrew.app.common.NoReviewsException if assignment has no reviews
     * @throws com.cornercrew.app.common.RatingBelowThresholdException if average rating is below threshold
     * @throws com.cornercrew.app.common.InvalidStatusTransitionException if assignment is not COMPLETED
     * @throws jakarta.persistence.EntityNotFoundException if assignment does not exist
     */
    PayoutResultDto processPayout(Long assignmentId, Long adminId);
}
