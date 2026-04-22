package com.cornercrew.app.campaignmap;

import com.cornercrew.app.campaign.*;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CampaignMapServiceImpl implements CampaignMapService {

    private final CampaignRepository campaignRepository;
    private final CampaignIntersectionRepository campaignIntersectionRepository;
    private final IntersectionRepository intersectionRepository;

    public CampaignMapServiceImpl(CampaignRepository campaignRepository,
                                  CampaignIntersectionRepository campaignIntersectionRepository,
                                  IntersectionRepository intersectionRepository) {
        this.campaignRepository = campaignRepository;
        this.campaignIntersectionRepository = campaignIntersectionRepository;
        this.intersectionRepository = intersectionRepository;
    }

    @Override
    public List<CampaignMapDto> getCampaignsForMap(List<CampaignStatus> statuses,
                                                    Double latitude,
                                                    Double longitude,
                                                    Double radiusKm) {
        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(CampaignStatus.OPEN, CampaignStatus.FUNDED);
        }

        List<Campaign> campaigns = campaignRepository.findByStatusIn(statuses);
        List<CampaignMapDto> result = new ArrayList<>();

        for (Campaign campaign : campaigns) {
            List<CampaignIntersection> campaignIntersections =
                    campaignIntersectionRepository.findByCampaignId(campaign.getId());

            List<IntersectionMapDto> intersectionDtos = new ArrayList<>();
            for (CampaignIntersection ci : campaignIntersections) {
                Optional<Intersection> optIntersection =
                        intersectionRepository.findById(ci.getIntersectionId());
                if (optIntersection.isEmpty()) {
                    continue;
                }
                Intersection intersection = optIntersection.get();
                if (intersection.getLatitude() == null || intersection.getLongitude() == null) {
                    continue;
                }
                intersectionDtos.add(new IntersectionMapDto(
                        intersection.getId(),
                        intersection.getLabel(),
                        intersection.getLatitude(),
                        intersection.getLongitude(),
                        intersection.getCongestionScore()
                ));
            }

            // Exclude campaigns with no geo-located intersections
            if (intersectionDtos.isEmpty()) {
                continue;
            }

            // If lat/lng/radius provided, filter by proximity
            if (latitude != null && longitude != null && radiusKm != null) {
                boolean hasIntersectionWithinRadius = intersectionDtos.stream()
                        .anyMatch(i -> haversineKm(latitude, longitude,
                                i.latitude(), i.longitude()) <= radiusKm);
                if (!hasIntersectionWithinRadius) {
                    continue;
                }
            }

            double fundingPercentage = computeFundingPercentage(
                    campaign.getCurrentAmount(), campaign.getTargetAmount());

            result.add(new CampaignMapDto(
                    campaign.getId(),
                    campaign.getTitle(),
                    campaign.getStatus(),
                    campaign.getTargetAmount(),
                    campaign.getCurrentAmount(),
                    fundingPercentage,
                    intersectionDtos
            ));
        }

        return result;
    }

    private static double computeFundingPercentage(BigDecimal currentAmount, BigDecimal targetAmount) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return currentAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
