package com.cornercrew.app.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Contribution c WHERE c.campaignId = :campaignId")
    BigDecimal sumByCampaignId(@Param("campaignId") Long campaignId);
}
