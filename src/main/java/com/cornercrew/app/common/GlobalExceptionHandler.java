package com.cornercrew.app.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CampaignNotOpenException.class)
    public ResponseEntity<ErrorResponse> handleCampaignNotOpen(CampaignNotOpenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CAMPAIGN_NOT_OPEN", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(ContributionExceedsCapException.class)
    public ResponseEntity<ErrorResponse> handleContributionExceedsCap(ContributionExceedsCapException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONTRIBUTION_EXCEEDS_CAP", ex.getMessage(),
                        Map.of("remainingCapacity", ex.getRemainingCapacity())));
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateApplication(DuplicateApplicationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_APPLICATION", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateReview(DuplicateReviewException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_REVIEW", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(ShiftConflictException.class)
    public ResponseEntity<ErrorResponse> handleShiftConflict(ShiftConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SHIFT_CONFLICT", ex.getMessage(),
                        Map.of("conflictingSlots", ex.getConflictingSlots())));
    }

    @ExceptionHandler(RatingBelowThresholdException.class)
    public ResponseEntity<ErrorResponse> handleRatingBelowThreshold(RatingBelowThresholdException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("RATING_BELOW_THRESHOLD", ex.getMessage(),
                        Map.of("avgRating", ex.getAvgRating(), "threshold", ex.getThreshold())));
    }

    @ExceptionHandler(AssignmentAlreadyPaidException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentAlreadyPaid(AssignmentAlreadyPaidException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ASSIGNMENT_ALREADY_PAID", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(NoReviewsException.class)
    public ResponseEntity<ErrorResponse> handleNoReviews(NoReviewsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("NO_REVIEWS", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATUS_TRANSITION", ex.getMessage(),
                        Map.of("currentStatus", ex.getCurrentStatus())));
    }

    @ExceptionHandler(TrafficApiUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleTrafficApiUnavailable(TrafficApiUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("TRAFFIC_API_UNAVAILABLE", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(GeoLocationApiUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleGeoLocationApiUnavailable(GeoLocationApiUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("GEOLOCATION_API_UNAVAILABLE", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOTIFICATION_NOT_FOUND", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(NotificationOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleNotificationOwnership(NotificationOwnershipException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("NOTIFICATION_FORBIDDEN", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(CampaignNotAcceptingApplicationsException.class)
    public ResponseEntity<ErrorResponse> handleCampaignNotAcceptingApplications(CampaignNotAcceptingApplicationsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("CAMPAIGN_NOT_ACCEPTING", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(CommuteProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommuteProfileNotFound(CommuteProfileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("COMMUTE_PROFILE_NOT_FOUND", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(InvalidCoordinatesException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCoordinates(InvalidCoordinatesException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_COORDINATES", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(InvalidTimeWindowException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTimeWindow(InvalidTimeWindowException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_TIME_WINDOW", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(RadiusExceedsMaxException.class)
    public ResponseEntity<ErrorResponse> handleRadiusExceedsMax(RadiusExceedsMaxException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("RADIUS_EXCEEDS_MAX", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(LocationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleLocationRequired(LocationRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("LOCATION_REQUIRED", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, Object> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> (Object) fe.getDefaultMessage(),
                        (a, b) -> a
                ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Validation failed", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        // Re-throw so Spring Security's AccessDeniedHandler returns 403
        throw ex;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("AUTHENTICATION_FAILED", "Invalid email or password", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", Map.of()));
    }
}
