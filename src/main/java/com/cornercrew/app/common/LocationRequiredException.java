package com.cornercrew.app.common;

public class LocationRequiredException extends RuntimeException {

    public LocationRequiredException(String message) {
        super(message);
    }
}
