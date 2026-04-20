package com.cornercrew.app.intersection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IntersectionCandidateService {

    /**
     * Flags the intersection if it is in CANDIDATE status and not already flagged.
     * Idempotent: calling on an already-FLAGGED, CONFIRMED, or DISMISSED intersection is a no-op.
     */
    void flagIfNotAlready(Long intersectionId);

    /**
     * Admin confirms a FLAGGED intersection. Transitions status to CONFIRMED
     * and triggers campaign auto-proposal.
     */
    IntersectionCandidateDto confirm(Long intersectionId, Long adminId);

    /**
     * Admin dismisses a FLAGGED intersection. Transitions status to DISMISSED.
     * A dismissed intersection can be re-flagged on the next poll cycle if
     * congestion remains above threshold.
     */
    IntersectionCandidateDto dismiss(Long intersectionId, Long adminId);

    /**
     * Returns all intersections with the given status, paginated.
     */
    Page<IntersectionCandidateDto> listByStatus(IntersectionStatus status, Pageable pageable);
}
