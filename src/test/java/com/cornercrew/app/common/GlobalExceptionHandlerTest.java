package com.cornercrew.app.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleCampaignNotOpen_returns409WithErrorCode() {
        var ex = new CampaignNotOpenException(42L);
        ResponseEntity<ErrorResponse> response = handler.handleCampaignNotOpen(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CAMPAIGN_NOT_OPEN", response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("42"));
        assertTrue(response.getBody().details().isEmpty());
    }

    @Test
    void handleContributionExceedsCap_returns409WithRemainingCapacity() {
        var remaining = new BigDecimal("150.00");
        var ex = new ContributionExceedsCapException(remaining);
        ResponseEntity<ErrorResponse> response = handler.handleContributionExceedsCap(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONTRIBUTION_EXCEEDS_CAP", response.getBody().errorCode());
        assertEquals(remaining, response.getBody().details().get("remainingCapacity"));
    }

    @Test
    void handleDuplicateApplication_returns409() {
        var ex = new DuplicateApplicationException(1L, 2L);
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateApplication(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_APPLICATION", response.getBody().errorCode());
    }

    @Test
    void handleShiftConflict_returns409WithConflictingSlots() {
        var slots = List.of("2025-08-04_MORNING", "2025-08-04_EVENING");
        var ex = new ShiftConflictException(slots);
        ResponseEntity<ErrorResponse> response = handler.handleShiftConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SHIFT_CONFLICT", response.getBody().errorCode());
        assertEquals(slots, response.getBody().details().get("conflictingSlots"));
    }

    @Test
    void handleRatingBelowThreshold_returns422WithRatingDetails() {
        var ex = new RatingBelowThresholdException(2.5, 3.0);
        ResponseEntity<ErrorResponse> response = handler.handleRatingBelowThreshold(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("RATING_BELOW_THRESHOLD", response.getBody().errorCode());
        assertEquals(2.5, response.getBody().details().get("avgRating"));
        assertEquals(3.0, response.getBody().details().get("threshold"));
    }

    @Test
    void handleAssignmentAlreadyPaid_returns409() {
        var ex = new AssignmentAlreadyPaidException(99L);
        ResponseEntity<ErrorResponse> response = handler.handleAssignmentAlreadyPaid(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ASSIGNMENT_ALREADY_PAID", response.getBody().errorCode());
    }

    @Test
    void handleNoReviews_returns422() {
        var ex = new NoReviewsException(77L);
        ResponseEntity<ErrorResponse> response = handler.handleNoReviews(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("NO_REVIEWS", response.getBody().errorCode());
    }

    @Test
    void handleInvalidStatusTransition_returns409WithCurrentStatus() {
        var ex = new InvalidStatusTransitionException("CONFIRMED", "FLAGGED");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidStatusTransition(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INVALID_STATUS_TRANSITION", response.getBody().errorCode());
        assertEquals("CONFIRMED", response.getBody().details().get("currentStatus"));
    }

    @Test
    void handleTrafficApiUnavailable_returns503() {
        var ex = new TrafficApiUnavailableException("Traffic API down");
        ResponseEntity<ErrorResponse> response = handler.handleTrafficApiUnavailable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("TRAFFIC_API_UNAVAILABLE", response.getBody().errorCode());
    }

    @Test
    void handleTrafficApiUnavailable_withCause_returns503() {
        var cause = new RuntimeException("connection refused");
        var ex = new TrafficApiUnavailableException("Traffic API down", cause);
        ResponseEntity<ErrorResponse> response = handler.handleTrafficApiUnavailable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("TRAFFIC_API_UNAVAILABLE", response.getBody().errorCode());
    }

    @Test
    void handleGeoLocationApiUnavailable_returns503() {
        var ex = new GeoLocationApiUnavailableException("Geo API down");
        ResponseEntity<ErrorResponse> response = handler.handleGeoLocationApiUnavailable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("GEOLOCATION_API_UNAVAILABLE", response.getBody().errorCode());
    }

    @Test
    void handleGeoLocationApiUnavailable_withCause_returns503() {
        var cause = new RuntimeException("timeout");
        var ex = new GeoLocationApiUnavailableException("Geo API down", cause);
        ResponseEntity<ErrorResponse> response = handler.handleGeoLocationApiUnavailable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("GEOLOCATION_API_UNAVAILABLE", response.getBody().errorCode());
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "title", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        var ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().errorCode());
        assertEquals("Validation failed", response.getBody().message());
        assertEquals("must not be blank", response.getBody().details().get("title"));
    }

    @Test
    void handleGenericException_returns500WithInternalError() {
        var ex = new RuntimeException("Something went wrong");
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().errorCode());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertTrue(response.getBody().details().isEmpty());
    }
}
