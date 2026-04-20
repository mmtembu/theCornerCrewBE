package com.cornercrew.app.common;

public class DuplicateReviewException extends RuntimeException {

    private final Long assignmentId;
    private final Long driverId;

    public DuplicateReviewException(Long assignmentId, Long driverId) {
        super("Driver " + driverId + " has already submitted a review for assignment " + assignmentId);
        this.assignmentId = assignmentId;
        this.driverId = driverId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public Long getDriverId() {
        return driverId;
    }
}
