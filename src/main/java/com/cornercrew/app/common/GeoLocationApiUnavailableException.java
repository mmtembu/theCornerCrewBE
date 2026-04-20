package com.cornercrew.app.common;

public class GeoLocationApiUnavailableException extends RuntimeException {

    public GeoLocationApiUnavailableException(String message) {
        super(message);
    }

    public GeoLocationApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
