package com.cornercrew.app.campaign;

import jakarta.persistence.*;

@Entity
@Table(name = "campaign_intersections")
public class CampaignIntersection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    public Long getIntersectionId() { return intersectionId; }
    public void setIntersectionId(Long intersectionId) { this.intersectionId = intersectionId; }
}
