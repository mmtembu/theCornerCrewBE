package com.cornercrew.app.common;

public class NoReviewsException extends RuntimeException {

    private final Long assignmentId;

    public NoReviewsException(Long assignmentId) {
        super("Assignment " + assignmentId + " has no reviews");
        this.assignmentId = assignmentId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }
}
