package com.cornercrew.app.common;

public class AssignmentAlreadyPaidException extends RuntimeException {

    private final Long assignmentId;

    public AssignmentAlreadyPaidException(Long assignmentId) {
        super("Assignment " + assignmentId + " has already been paid");
        this.assignmentId = assignmentId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }
}
