package com.cornercrew.app.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignIntersectionRepository extends JpaRepository<CampaignIntersection, Long> {

    List<CampaignIntersection> findByCampaignId(Long campaignId);

    List<CampaignIntersection> findByIntersectionId(Long intersectionId);

    boolean existsByCampaignIdAndIntersectionId(Long campaignId, Long intersectionId);
}
