package com.cornercrew.app.common;

import java.util.Map;

public record ErrorResponse(
        String errorCode,
        String message,
        Map<String, Object> details
) {}
