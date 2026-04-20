package com.cornercrew.app.assignment;

import com.cornercrew.app.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/campaigns/{id}/assignments")
    public ResponseEntity<AssignmentDto> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignControllerRequest request,
            @AuthenticationPrincipal User admin) {
        Long adminId = admin != null ? admin.getId() : null;
        AssignmentDto assignment = assignmentService.assign(id, request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @GetMapping("/campaigns/{id}/assignments")
    public ResponseEntity<List<AssignmentDto>> listAssignments(@PathVariable Long id) {
        List<AssignmentDto> assignments = assignmentService.listAssignments(id);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<AssignmentDto> getAssignment(@PathVariable Long id) {
        AssignmentDto assignment = assignmentService.getAssignment(id);
        return ResponseEntity.ok(assignment);
    }
}
