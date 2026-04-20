package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.DuplicateApplicationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CampaignRepository campaignRepository;

    public ApplicationServiceImpl(ApplicationRepository applicationRepository,
                                  CampaignRepository campaignRepository) {
        this.applicationRepository = applicationRepository;
        this.campaignRepository = campaignRepository;
    }

    @Override
    @PreAuthorize("hasRole('CONTROLLER')")
    public ApplicationDto apply(Long campaignId, Long controllerId, ApplyRequest req) {
        // Check for duplicate application
        applicationRepository.findByCampaignIdAndControllerId(campaignId, controllerId)
                .ifPresent(existing -> {
                    throw new DuplicateApplicationException(campaignId, controllerId);
                });

        // Validate campaign status is OPEN or FUNDED
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.OPEN && campaign.getStatus() != CampaignStatus.FUNDED) {
            throw new IllegalStateException(
                    "Cannot apply to campaign with status " + campaign.getStatus() + "; campaign must be OPEN or FUNDED");
        }

        // Create application with PENDING status
        ControllerApplication application = new ControllerApplication();
        application.setCampaignId(campaignId);
        application.setControllerId(controllerId);
        application.setStatus(ApplicationStatus.PENDING);
        application.setNote(req != null ? req.note() : null);
        application.setAppliedAt(OffsetDateTime.now());

        ControllerApplication saved = applicationRepository.save(application);
        return toDto(saved);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<ApplicationDto> listApplications(Long campaignId) {
        return applicationRepository.findByCampaignId(campaignId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ApplicationDto updateStatus(Long applicationId, ApplicationStatus status) {
        ControllerApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Application not found with id: " + applicationId));

        application.setStatus(status);
        ControllerApplication saved = applicationRepository.save(application);
        return toDto(saved);
    }

    private ApplicationDto toDto(ControllerApplication app) {
        return new ApplicationDto(
                app.getId(),
                app.getCampaignId(),
                app.getControllerId(),
                app.getStatus(),
                app.getNote(),
                app.getAppliedAt()
        );
    }
}
