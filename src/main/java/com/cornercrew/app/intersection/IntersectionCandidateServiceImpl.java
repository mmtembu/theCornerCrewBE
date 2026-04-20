package com.cornercrew.app.intersection;

import com.cornercrew.app.campaign.CampaignService;
import com.cornercrew.app.common.InvalidStatusTransitionException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IntersectionCandidateServiceImpl implements IntersectionCandidateService {

    private final IntersectionRepository intersectionRepository;
    private final CampaignService campaignService;

    public IntersectionCandidateServiceImpl(IntersectionRepository intersectionRepository,
                                            CampaignService campaignService) {
        this.intersectionRepository = intersectionRepository;
        this.campaignService = campaignService;
    }

    @Override
    public void flagIfNotAlready(Long intersectionId) {
        Intersection intersection = intersectionRepository.findById(intersectionId)
                .orElseThrow(() -> new EntityNotFoundException("Intersection not found with id: " + intersectionId));

        // Idempotent: only transition from CANDIDATE to FLAGGED
        if (intersection.getStatus() == IntersectionStatus.CANDIDATE) {
            intersection.setStatus(IntersectionStatus.FLAGGED);
            intersectionRepository.save(intersection);
        }
        // If already FLAGGED, CONFIRMED, or DISMISSED: no-op
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public IntersectionCandidateDto confirm(Long intersectionId, Long adminId) {
        Intersection intersection = intersectionRepository.findById(intersectionId)
                .orElseThrow(() -> new EntityNotFoundException("Intersection not found with id: " + intersectionId));

        // Enforce valid transition: FLAGGED -> CONFIRMED
        if (intersection.getStatus() != IntersectionStatus.FLAGGED) {
            throw new InvalidStatusTransitionException(
                    intersection.getStatus().name(),
                    IntersectionStatus.CONFIRMED.name()
            );
        }

        intersection.setStatus(IntersectionStatus.CONFIRMED);
        Intersection saved = intersectionRepository.save(intersection);

        // Trigger auto-proposed campaign
        campaignService.autoProposeCampaign(intersectionId, adminId);

        return toDto(saved);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public IntersectionCandidateDto dismiss(Long intersectionId, Long adminId) {
        Intersection intersection = intersectionRepository.findById(intersectionId)
                .orElseThrow(() -> new EntityNotFoundException("Intersection not found with id: " + intersectionId));

        // Enforce valid transition: FLAGGED -> DISMISSED
        if (intersection.getStatus() != IntersectionStatus.FLAGGED) {
            throw new InvalidStatusTransitionException(
                    intersection.getStatus().name(),
                    IntersectionStatus.DISMISSED.name()
            );
        }

        intersection.setStatus(IntersectionStatus.DISMISSED);
        Intersection saved = intersectionRepository.save(intersection);

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IntersectionCandidateDto> listByStatus(IntersectionStatus status, Pageable pageable) {
        return intersectionRepository.findByStatus(status, pageable)
                .map(this::toDto);
    }

    private IntersectionCandidateDto toDto(Intersection intersection) {
        return new IntersectionCandidateDto(
                intersection.getId(),
                intersection.getLabel(),
                intersection.getDescription(),
                intersection.getLatitude(),
                intersection.getLongitude(),
                intersection.getType(),
                intersection.getStatus(),
                intersection.getCongestionScore(),
                intersection.getLastCheckedAt()
        );
    }
}
