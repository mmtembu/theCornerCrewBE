package com.cornercrew.app.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/location")
public class UserLocationController {

    private final UserLocationService userLocationService;

    public UserLocationController(UserLocationService userLocationService) {
        this.userLocationService = userLocationService;
    }

    @PutMapping
    public ResponseEntity<Void> setLocation(
            @Valid @RequestBody SetLocationRequest request,
            @AuthenticationPrincipal User user) {
        userLocationService.setLocation(user.getId(), request.latitude(), request.longitude());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<UserLocationDto> getLocation(
            @AuthenticationPrincipal User user) {
        return userLocationService.getLocation(user.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
