package com.cornercrew.app.assignment;

import com.cornercrew.app.common.DuplicateReviewException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final AssignmentRepository assignmentRepository;

    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             AssignmentRepository assignmentRepository) {
        this.reviewRepository = reviewRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @PreAuthorize("hasRole('DRIVER')")
    public ReviewDto submitReview(Long assignmentId, Long driverId, SubmitReviewRequest req) {
        // Validate: assignment exists
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found with id: " + assignmentId));

        // Validate: assignment status is COMPLETED
        if (assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "Cannot submit review for assignment with status " + assignment.getStatus() + 
                    "; assignment must be COMPLETED");
        }

        // Validate: no duplicate review (assignmentId + driverId)
        reviewRepository.findByAssignmentIdAndDriverId(assignmentId, driverId)
                .ifPresent(existing -> {
                    throw new DuplicateReviewException(assignmentId, driverId);
                });

        // Validate: rating in [1, 5] - handled by @Min/@Max on SubmitReviewRequest
        // but we add explicit validation for safety
        if (req.rating() < 1 || req.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5, got: " + req.rating());
        }

        // Create and persist review
        Review review = new Review();
        review.setAssignmentId(assignmentId);
        review.setDriverId(driverId);
        review.setRating(req.rating());
        review.setComment(req.comment());
        review.setReviewedAt(OffsetDateTime.now());

        Review saved = reviewRepository.save(review);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryDto getSummary(Long assignmentId) {
        // Validate: assignment exists
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new EntityNotFoundException("Assignment not found with id: " + assignmentId);
        }

        long reviewCount = reviewRepository.countByAssignmentId(assignmentId);
        double avgRating = reviewRepository.averageRatingByAssignmentId(assignmentId);

        return new ReviewSummaryDto(avgRating, reviewCount);
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getAssignmentId(),
                review.getDriverId(),
                review.getRating(),
                review.getComment(),
                review.getReviewedAt()
        );
    }
}
