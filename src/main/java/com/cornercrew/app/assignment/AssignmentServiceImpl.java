package com.cornercrew.app.assignment;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.ShiftConflictException;
import com.cornercrew.app.intersection.IntersectionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ShiftSlotRepository shiftSlotRepository;
    private final CampaignRepository campaignRepository;
    private final ApplicationRepository applicationRepository;
    private final IntersectionRepository intersectionRepository;

    public AssignmentServiceImpl(
            AssignmentRepository assignmentRepository,
            ShiftSlotRepository shiftSlotRepository,
            CampaignRepository campaignRepository,
            ApplicationRepository applicationRepository,
            IntersectionRepository intersectionRepository) {
        this.assignmentRepository = assignmentRepository;
        this.shiftSlotRepository = shiftSlotRepository;
        this.campaignRepository = campaignRepository;
        this.applicationRepository = applicationRepository;
        this.intersectionRepository = intersectionRepository;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public AssignmentDto assign(Long campaignId, AssignControllerRequest req, Long adminId) {
        // Validate: campaign status is FUNDED
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with id: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.FUNDED) {
            throw new IllegalArgumentException("Campaign must be FUNDED to assign controllers. Current status: " + campaign.getStatus());
        }

        // Validate: controller has an ACCEPTED application for this campaign
        ControllerApplication application = applicationRepository
                .findByCampaignIdAndControllerId(campaignId, req.controllerId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "No application found for controller " + req.controllerId() + " in campaign " + campaignId));

        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new IllegalArgumentException("Controller application must be ACCEPTED. Current status: " + application.getStatus());
        }

        // Validate: intersection exists
        if (!intersectionRepository.existsById(req.intersectionId())) {
            throw new EntityNotFoundException("Intersection not found with id: " + req.intersectionId());
        }

        // Validate: no shift conflicts
        List<String> conflicts = new ArrayList<>();
        for (LocalDate date : req.shiftDates()) {
            if (shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                    req.intersectionId(), date, ShiftType.MORNING)) {
                conflicts.add(date + "_MORNING");
            }
            if (shiftSlotRepository.existsByIntersectionIdAndDateAndShiftType(
                    req.intersectionId(), date, ShiftType.EVENING)) {
                conflicts.add(date + "_EVENING");
            }
        }

        if (!conflicts.isEmpty()) {
            throw new ShiftConflictException(conflicts);
        }

        // Create Assignment with status ASSIGNED
        Assignment assignment = new Assignment();
        assignment.setCampaignId(campaignId);
        assignment.setControllerId(req.controllerId());
        assignment.setIntersectionId(req.intersectionId());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAgreedPay(req.agreedPay());

        assignment = assignmentRepository.save(assignment);

        // Create N * 2 ShiftSlot records: for each date, create MORNING and EVENING slots
        for (LocalDate date : req.shiftDates()) {
            // MORNING slot: 07:00-09:00
            ShiftSlot morningSlot = new ShiftSlot();
            morningSlot.setAssignmentId(assignment.getId());
            morningSlot.setIntersectionId(req.intersectionId());
            morningSlot.setDate(date);
            morningSlot.setShiftType(ShiftType.MORNING);
            morningSlot.setStartTime(LocalTime.of(7, 0));
            morningSlot.setEndTime(LocalTime.of(9, 0));
            shiftSlotRepository.save(morningSlot);

            // EVENING slot: 16:30-18:30
            ShiftSlot eveningSlot = new ShiftSlot();
            eveningSlot.setAssignmentId(assignment.getId());
            eveningSlot.setIntersectionId(req.intersectionId());
            eveningSlot.setDate(date);
            eveningSlot.setShiftType(ShiftType.EVENING);
            eveningSlot.setStartTime(LocalTime.of(16, 30));
            eveningSlot.setEndTime(LocalTime.of(18, 30));
            shiftSlotRepository.save(eveningSlot);
        }

        return toDto(assignment);
    }

    @Override
    public List<AssignmentDto> listAssignments(Long campaignId) {
        return assignmentRepository.findByCampaignId(campaignId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AssignmentDto getAssignment(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found with id: " + assignmentId));
        return toDto(assignment);
    }

    private AssignmentDto toDto(Assignment assignment) {
        return new AssignmentDto(
                assignment.getId(),
                assignment.getCampaignId(),
                assignment.getControllerId(),
                assignment.getIntersectionId(),
                assignment.getStatus(),
                assignment.getAgreedPay(),
                assignment.getAssignedAt(),
                assignment.getPaidAt()
        );
    }
}
