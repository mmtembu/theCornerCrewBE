package com.cornercrew.app.campaignmap;

import com.cornercrew.app.campaign.*;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.intersection.IntersectionStatus;
import com.cornercrew.app.intersection.IntersectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignMapServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignIntersectionRepository campaignIntersectionRepository;

    @Mock
    private IntersectionRepository intersectionRepository;

    @InjectMocks
    private CampaignMapServiceImpl campaignMapService;

    // --- Test 1: No location params returns all matching campaigns ---

    @Test
    void getCampaignsForMap_noLocationParams_returnsAllMatchingCampaigns() {
        Campaign campaign1 = buildCampaign(1L, CampaignStatus.OPEN);
        Campaign campaign2 = buildCampaign(2L, CampaignStatus.FUNDED);

        when(campaignRepository.findByStatusIn(List.of(CampaignStatus.OPEN, CampaignStatus.FUNDED)))
                .thenReturn(List.of(campaign1, campaign2));

        CampaignIntersection ci1 = buildCampaignIntersection(1L, 1L, 10L);
        CampaignIntersection ci2 = buildCampaignIntersection(2L, 2L, 20L);

        when(campaignIntersectionRepository.findByCampaignId(1L)).thenReturn(List.of(ci1));
        when(campaignIntersectionRepository.findByCampaignId(2L)).thenReturn(List.of(ci2));

        Intersection intersection1 = buildIntersection(10L, "Main & 1st", 37.7749, -122.4194);
        Intersection intersection2 = buildIntersection(20L, "Oak & 2nd", 37.7850, -122.4094);

        when(intersectionRepository.findById(10L)).thenReturn(Optional.of(intersection1));
        when(intersectionRepository.findById(20L)).thenReturn(Optional.of(intersection2));

        List<CampaignMapDto> result = campaignMapService.getCampaignsForMap(
                null, null, null, null);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).campaignId());
        assertEquals(2L, result.get(1).campaignId());
        // Each campaign should have one intersection
        assertEquals(1, result.get(0).intersections().size());
        assertEquals(1, result.get(1).intersections().size());
    }

    // --- Test 2: Campaigns without intersections are excluded ---

    @Test
    void getCampaignsForMap_campaignWithoutIntersections_isExcluded() {
        Campaign campaignWithIntersections = buildCampaign(1L, CampaignStatus.OPEN);
        Campaign campaignWithoutIntersections = buildCampaign(2L, CampaignStatus.OPEN);

        when(campaignRepository.findByStatusIn(List.of(CampaignStatus.OPEN, CampaignStatus.FUNDED)))
                .thenReturn(List.of(campaignWithIntersections, campaignWithoutIntersections));

        CampaignIntersection ci = buildCampaignIntersection(1L, 1L, 10L);
        when(campaignIntersectionRepository.findByCampaignId(1L)).thenReturn(List.of(ci));
        // Campaign 2 has no campaign-intersection records
        when(campaignIntersectionRepository.findByCampaignId(2L)).thenReturn(Collections.emptyList());

        Intersection intersection = buildIntersection(10L, "Main & 1st", 37.7749, -122.4194);
        when(intersectionRepository.findById(10L)).thenReturn(Optional.of(intersection));

        List<CampaignMapDto> result = campaignMapService.getCampaignsForMap(
                null, null, null, null);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).campaignId());
    }

    // --- Test 3: Status filtering works correctly ---

    @Test
    void getCampaignsForMap_statusFilter_returnsOnlyMatchingStatus() {
        Campaign fundedCampaign = buildCampaign(1L, CampaignStatus.FUNDED);

        when(campaignRepository.findByStatusIn(List.of(CampaignStatus.FUNDED)))
                .thenReturn(List.of(fundedCampaign));

        CampaignIntersection ci = buildCampaignIntersection(1L, 1L, 10L);
        when(campaignIntersectionRepository.findByCampaignId(1L)).thenReturn(List.of(ci));

        Intersection intersection = buildIntersection(10L, "Main & 1st", 37.7749, -122.4194);
        when(intersectionRepository.findById(10L)).thenReturn(Optional.of(intersection));

        List<CampaignMapDto> result = campaignMapService.getCampaignsForMap(
                List.of(CampaignStatus.FUNDED), null, null, null);

        assertEquals(1, result.size());
        assertEquals(CampaignStatus.FUNDED, result.get(0).status());
        assertEquals(1L, result.get(0).campaignId());

        // Verify the repository was called with FUNDED only
        verify(campaignRepository).findByStatusIn(List.of(CampaignStatus.FUNDED));
    }

    // --- Helper methods ---

    private Campaign buildCampaign(Long id, CampaignStatus status) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setTitle("Campaign " + id);
        c.setDescription("Description for campaign " + id);
        c.setTargetAmount(new BigDecimal("1000.00"));
        c.setCurrentAmount(new BigDecimal("500.00"));
        c.setStatus(status);
        c.setWindowStart(LocalDate.now().plusDays(1));
        c.setWindowEnd(LocalDate.now().plusDays(30));
        c.setCreatedByAdminId(1L);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    private CampaignIntersection buildCampaignIntersection(Long id, Long campaignId, Long intersectionId) {
        CampaignIntersection ci = new CampaignIntersection();
        ci.setId(id);
        ci.setCampaignId(campaignId);
        ci.setIntersectionId(intersectionId);
        return ci;
    }

    private Intersection buildIntersection(Long id, String label, Double lat, Double lng) {
        Intersection i = new Intersection();
        i.setId(id);
        i.setLabel(label);
        i.setLatitude(lat);
        i.setLongitude(lng);
        i.setType(IntersectionType.FOUR_WAY_STOP);
        i.setStatus(IntersectionStatus.CONFIRMED);
        i.setCongestionScore(0.5);
        return i;
    }
}
