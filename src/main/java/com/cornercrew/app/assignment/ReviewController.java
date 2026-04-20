package com.cornercrew.app.assignment;

import com.cornercrew.app.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assignments/{id}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewDto> submitReview(
            @PathVariable Long id,
            @Valid @RequestBody SubmitReviewRequest request,
            @AuthenticationPrincipal User driver) {
        Long driverId = driver != null ? driver.getId() : null;
        ReviewDto review = reviewService.submitReview(id, driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    @GetMapping("/summary")
    public ResponseEntity<ReviewSummaryDto> getSummary(@PathVariable Long id) {
        ReviewSummaryDto summary = reviewService.getSummary(id);
        return ResponseEntity.ok(summary);
    }
}
