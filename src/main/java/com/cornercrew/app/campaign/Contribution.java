package com.cornercrew.app.campaign;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "contributions")
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ContributionPeriod period;

    @Column(name = "contributed_at", nullable = false)
    private OffsetDateTime contributedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public ContributionPeriod getPeriod() { return period; }
    public void setPeriod(ContributionPeriod period) { this.period = period; }

    public OffsetDateTime getContributedAt() { return contributedAt; }
    public void setContributedAt(OffsetDateTime contributedAt) { this.contributedAt = contributedAt; }
}
