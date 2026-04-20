package com.cornercrew.app.campaign;

import com.cornercrew.app.common.InvalidStatusTransitionException;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@Transactional
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final IntersectionRepository intersectionRepository;

    public CampaignServiceImpl(CampaignRepository campaignRepository,
                               IntersectionRepository intersectionRepository) {
        this.campaignRepository = campaignRepository;
        this.intersectionRepository = intersectionRepository;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CampaignDto createCampaign(CreateCampaignRequest req, Long adminId) {
        if (!req.windowStart().isBefore(req.windowEnd())) {
            throw new IllegalArgumentException("windowStart must be before windowEnd");
        }
        if (!req.windowEnd().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("windowEnd must be in the future");
        }
        if (req.targetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetAmount must be greater than 0");
        }

        Campaign campaign = new Campaign();
        campaign.setTitle(req.title());
        campaign.setDescription(req.description());
        campaign.setTargetAmount(req.targetAmount());
        campaign.setCurrentAmount(BigDecimal.ZERO);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setWindowStart(req.windowStart());
        campaign.setWindowEnd(req.windowEnd());
        campaign.setCreatedByAdminId(adminId);
        campaign.setCreatedAt(OffsetDateTime.now());

        Campaign saved = campaignRepository.save(campaign);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDto getCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));
        return toDto(campaign);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CampaignDto> listCampaigns(CampaignStatus status, Pageable pageable) {
        if (status == null) {
            return campaignRepository.findAll(pageable).map(this::toDto);
        }
        return campaignRepository.findByStatus(status, pageable).map(this::toDto);
    }

    @Override
    public void checkAndLockIfFunded(Long campaignId) {
        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));

        if (campaign.getCurrentAmount().compareTo(campaign.getTargetAmount()) >= 0
                && campaign.getStatus() == CampaignStatus.OPEN) {
            campaign.setStatus(CampaignStatus.FUNDED);
            campaign.setLockedAt(OffsetDateTime.now());
            campaignRepository.save(campaign);
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CampaignDto autoProposeCampaign(Long intersectionId, Long adminId) {
        Intersection intersection = intersectionRepository.findById(intersectionId)
                .orElseThrow(() -> new EntityNotFoundException("Intersection not found with id: " + intersectionId));

        Campaign campaign = new Campaign();
        campaign.setTitle("Traffic Control: " + intersection.getLabel());
        campaign.setDescription("Auto-proposed campaign for intersection: " + intersection.getLabel());
        campaign.setTargetAmount(new BigDecimal("5000.00"));
        campaign.setCurrentAmount(BigDecimal.ZERO);
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setWindowStart(LocalDate.now());
        campaign.setWindowEnd(LocalDate.now().plusDays(30));
        campaign.setCreatedByAdminId(adminId);
        campaign.setCreatedAt(OffsetDateTime.now());

        Campaign saved = campaignRepository.save(campaign);
        return toDto(saved);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CampaignDto approveCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidStatusTransitionException(
                    campaign.getStatus().name(), CampaignStatus.OPEN.name());
        }

        campaign.setStatus(CampaignStatus.OPEN);
        Campaign saved = campaignRepository.save(campaign);
        return toDto(saved);
    }

    private CampaignDto toDto(Campaign campaign) {
        return new CampaignDto(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getTargetAmount(),
                campaign.getCurrentAmount(),
                campaign.getStatus(),
                campaign.getWindowStart(),
                campaign.getWindowEnd(),
                campaign.getLockedAt(),
                campaign.getCreatedAt()
        );
    }
}
