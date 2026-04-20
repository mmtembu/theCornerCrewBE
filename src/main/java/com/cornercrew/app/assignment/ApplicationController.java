package com.cornercrew.app.assignment;

import com.cornercrew.app.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/campaigns/{id}/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> apply(
            @PathVariable Long id,
            @RequestBody(required = false) ApplyRequest request,
            @AuthenticationPrincipal User controller) {
        Long controllerId = controller != null ? controller.getId() : null;
        ApplicationDto application = applicationService.apply(id, controllerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(application);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationDto>> listApplications(@PathVariable Long id) {
        List<ApplicationDto> applications = applicationService.listApplications(id);
        return ResponseEntity.ok(applications);
    }

    @PutMapping("/{appId}/status")
    public ResponseEntity<ApplicationDto> updateStatus(
            @PathVariable Long id,
            @PathVariable Long appId,
            @RequestBody ApplicationStatus status) {
        ApplicationDto application = applicationService.updateStatus(appId, status);
        return ResponseEntity.ok(application);
    }
}
