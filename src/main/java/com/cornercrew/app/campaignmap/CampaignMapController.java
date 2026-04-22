package com.cornercrew.app.campaignmap;

import com.cornercrew.app.campaign.CampaignStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/campaigns/map")
public class CampaignMapController {

    private final CampaignMapService campaignMapService;

    public CampaignMapController(CampaignMapService campaignMapService) {
        this.campaignMapService = campaignMapService;
    }

    @GetMapping
    public ResponseEntity<List<CampaignMapDto>> getCampaignsForMap(
            @RequestParam(required = false) List<CampaignStatus> statuses,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm) {
        List<CampaignMapDto> campaigns = campaignMapService.getCampaignsForMap(
                statuses, latitude, longitude, radiusKm);
        return ResponseEntity.ok(campaigns);
    }
}
