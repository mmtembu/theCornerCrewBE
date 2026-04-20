package com.cornercrew.app.campaign;

import com.cornercrew.app.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignDto> createCampaign(
            @Valid @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal User admin) {
        Long adminId = admin != null ? admin.getId() : null;
        CampaignDto campaign = campaignService.createCampaign(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(campaign);
    }

    @GetMapping
    public ResponseEntity<Page<CampaignDto>> listCampaigns(
            @RequestParam(required = false) CampaignStatus status,
            Pageable pageable) {
        Page<CampaignDto> campaigns = campaignService.listCampaigns(status, pageable);
        return ResponseEntity.ok(campaigns);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDto> getCampaign(@PathVariable Long id) {
        CampaignDto campaign = campaignService.getCampaign(id);
        return ResponseEntity.ok(campaign);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<CampaignDto> approveCampaign(@PathVariable Long id) {
        CampaignDto campaign = campaignService.approveCampaign(id);
        return ResponseEntity.ok(campaign);
    }
}
