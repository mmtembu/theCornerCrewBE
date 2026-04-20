package com.cornercrew.app.campaign;

import com.cornercrew.app.common.CampaignNotOpenException;
import com.cornercrew.app.common.ContributionExceedsCapException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@Transactional
public class FundingServiceImpl implements FundingService {

    private final CampaignRepository campaignRepository;
    private final ContributionRepository contributionRepository;
    private final CampaignService campaignService;

    public FundingServiceImpl(CampaignRepository campaignRepository,
                              ContributionRepository contributionRepository,
                              CampaignService campaignService) {
        this.campaignRepository = campaignRepository;
        this.contributionRepository = contributionRepository;
        this.campaignService = campaignService;
    }

    @Override
    @PreAuthorize("hasRole('DRIVER')")
    public ContributionDto contribute(Long campaignId, Long driverId, ContributeRequest req) {
        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.OPEN) {
            throw new CampaignNotOpenException(campaignId);
        }

        BigDecimal amount = req.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Contribution amount must be greater than 0");
        }

        BigDecimal remaining = campaign.getTargetAmount().subtract(campaign.getCurrentAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new ContributionExceedsCapException(remaining);
        }

        Contribution contribution = new Contribution();
        contribution.setCampaignId(campaignId);
        contribution.setDriverId(driverId);
        contribution.setAmount(amount);
        contribution.setPeriod(req.period());
        contribution.setContributedAt(OffsetDateTime.now());
        Contribution saved = contributionRepository.save(contribution);

        campaign.setCurrentAmount(campaign.getCurrentAmount().add(amount));
        campaignRepository.save(campaign);

        campaignService.checkAndLockIfFunded(campaignId);

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FundingSummaryDto getSummary(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));

        BigDecimal remaining = campaign.getTargetAmount().subtract(campaign.getCurrentAmount());
        return new FundingSummaryDto(campaign.getCurrentAmount(), remaining);
    }

    private ContributionDto toDto(Contribution c) {
        return new ContributionDto(
                c.getId(),
                c.getCampaignId(),
                c.getDriverId(),
                c.getAmount(),
                c.getPeriod(),
                c.getContributedAt()
        );
    }
}
