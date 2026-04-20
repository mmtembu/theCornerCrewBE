package com.cornercrew.app.assignment;

public interface ReviewService {
    ReviewDto submitReview(Long assignmentId, Long driverId, SubmitReviewRequest req);
    ReviewSummaryDto getSummary(Long assignmentId);
}
