package com.cornercrew.app.commuteprofile;

import com.cornercrew.app.common.CommuteProfileNotFoundException;
import com.cornercrew.app.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/commute-profile")
@PreAuthorize("hasRole('DRIVER')")
public class CommuteProfileController {

    private final CommuteProfileService commuteProfileService;

    public CommuteProfileController(CommuteProfileService commuteProfileService) {
        this.commuteProfileService = commuteProfileService;
    }

    @PutMapping
    public ResponseEntity<CommuteProfileDto> saveProfile(
            @Valid @RequestBody SaveCommuteProfileRequest request,
            @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        CommuteProfileService.SaveResult result = commuteProfileService.saveProfile(userId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.profile());
    }

    @GetMapping
    public ResponseEntity<CommuteProfileDto> getProfile(@AuthenticationPrincipal User user) {
        Long userId = user.getId();
        CommuteProfileDto profile = commuteProfileService.getProfile(userId)
                .orElseThrow(() -> new CommuteProfileNotFoundException(
                        "Commute profile not found for user " + userId));
        return ResponseEntity.ok(profile);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal User user) {
        Long userId = user.getId();
        commuteProfileService.deleteProfile(userId);
        return ResponseEntity.noContent().build();
    }
}
