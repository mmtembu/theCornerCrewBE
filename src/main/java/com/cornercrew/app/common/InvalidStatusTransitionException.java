package com.cornercrew.app.common;

public class InvalidStatusTransitionException extends RuntimeException {

    private final String currentStatus;
    private final String attemptedStatus;

    public InvalidStatusTransitionException(String currentStatus, String attemptedStatus) {
        super("Invalid status transition from " + currentStatus + " to " + attemptedStatus);
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getAttemptedStatus() {
        return attemptedStatus;
    }
}
