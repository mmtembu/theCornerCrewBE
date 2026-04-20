package com.cornercrew.app.common;

public class TrafficApiUnavailableException extends RuntimeException {

    public TrafficApiUnavailableException(String message) {
        super(message);
    }

    public TrafficApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
