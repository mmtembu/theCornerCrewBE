package com.cornercrew.app.campaignmap;

public record IntersectionMapDto(
        Long id,
        String label,
        double latitude,
        double longitude,
        Double congestionScore
) {}
