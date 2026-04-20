package com.cornercrew.app.intersection;

import java.time.OffsetDateTime;

public record IntersectionCandidateDto(
        Long id,
        String label,
        String description,
        Double latitude,
        Double longitude,
        IntersectionType type,
        IntersectionStatus status,
        Double congestionScore,
        OffsetDateTime lastCheckedAt
) {}
