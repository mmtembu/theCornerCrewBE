package com.cornercrew.app.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<ControllerApplication, Long> {

    Optional<ControllerApplication> findByCampaignIdAndControllerId(Long campaignId, Long controllerId);

    List<ControllerApplication> findByCampaignId(Long campaignId);
}
