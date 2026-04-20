package com.cornercrew.app.campaign;

import com.cornercrew.app.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns/{id}/contributions")
public class FundingController {

    private final FundingService fundingService;

    public FundingController(FundingService fundingService) {
        this.fundingService = fundingService;
    }

    @PostMapping
    public ResponseEntity<ContributionDto> contribute(
            @PathVariable Long id,
            @Valid @RequestBody ContributeRequest request,
            @AuthenticationPrincipal User driver) {
        Long driverId = driver != null ? driver.getId() : null;
        ContributionDto contribution = fundingService.contribute(id, driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(contribution);
    }

    @GetMapping("/summary")
    public ResponseEntity<FundingSummaryDto> getSummary(@PathVariable Long id) {
        FundingSummaryDto summary = fundingService.getSummary(id);
        return ResponseEntity.ok(summary);
    }
}
