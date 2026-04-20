package com.cornercrew.app.assignment;

import java.util.List;

public interface AssignmentService {

    /**
     * Assigns a controller to an intersection with shift schedule.
     * Validates campaign status, controller application, intersection existence,
     * and shift conflicts before creating the assignment.
     *
     * @param campaignId the campaign ID
     * @param req the assignment request containing controller, intersection, dates, and pay
     * @param adminId the admin user ID performing the assignment
     * @return the created assignment DTO
     */
    AssignmentDto assign(Long campaignId, AssignControllerRequest req, Long adminId);

    /**
     * Lists all assignments for a given campaign.
     *
     * @param campaignId the campaign ID
     * @return list of assignment DTOs
     */
    List<AssignmentDto> listAssignments(Long campaignId);

    /**
     * Retrieves a single assignment by ID.
     *
     * @param assignmentId the assignment ID
     * @return the assignment DTO
     */
    AssignmentDto getAssignment(Long assignmentId);
}
