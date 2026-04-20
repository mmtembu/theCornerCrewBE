package com.cornercrew.app.intersection;

import com.cornercrew.app.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/intersections/candidates")
public class IntersectionCandidateController {

    private final IntersectionCandidateService intersectionCandidateService;

    public IntersectionCandidateController(IntersectionCandidateService intersectionCandidateService) {
        this.intersectionCandidateService = intersectionCandidateService;
    }

    @GetMapping
    public ResponseEntity<Page<IntersectionCandidateDto>> listCandidates(
            @RequestParam(required = false) IntersectionStatus status,
            Pageable pageable) {
        Page<IntersectionCandidateDto> candidates = intersectionCandidateService.listByStatus(status, pageable);
        return ResponseEntity.ok(candidates);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<IntersectionCandidateDto> confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        Long adminId = admin != null ? admin.getId() : null;
        IntersectionCandidateDto result = intersectionCandidateService.confirm(id, adminId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<IntersectionCandidateDto> dismiss(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        Long adminId = admin != null ? admin.getId() : null;
        IntersectionCandidateDto result = intersectionCandidateService.dismiss(id, adminId);
        return ResponseEntity.ok(result);
    }
}
