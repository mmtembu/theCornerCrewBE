package com.cornercrew.app.incident;

import com.cornercrew.app.common.LocationRequiredException;
import com.cornercrew.app.common.RadiusExceedsMaxException;
import com.cornercrew.app.config.IncidentProperties;
import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserLocationDto;
import com.cornercrew.app.user.UserLocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/traffic/incidents")
public class TrafficIncidentController {

    private final TrafficIncidentService trafficIncidentService;
    private final UserLocationService userLocationService;
    private final IncidentProperties incidentProperties;

    public TrafficIncidentController(TrafficIncidentService trafficIncidentService,
                                     UserLocationService userLocationService,
                                     IncidentProperties incidentProperties) {
        this.trafficIncidentService = trafficIncidentService;
        this.userLocationService = userLocationService;
        this.incidentProperties = incidentProperties;
    }

    @GetMapping
    public ResponseEntity<List<TrafficIncidentDto>> getIncidents(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false, defaultValue = "5") Double radiusKm,
            @AuthenticationPrincipal User user) {

        if (radiusKm > incidentProperties.getMaxRadiusKm()) {
            throw new RadiusExceedsMaxException(
                    "Radius " + radiusKm + " km exceeds maximum allowed value of "
                            + incidentProperties.getMaxRadiusKm() + " km");
        }

        if (latitude == null || longitude == null) {
            Optional<UserLocationDto> storedLocation = userLocationService.getLocation(user.getId());
            if (storedLocation.isPresent()) {
                latitude = storedLocation.get().latitude();
                longitude = storedLocation.get().longitude();
            } else {
                throw new LocationRequiredException(
                        "Location parameters or a stored location preference are required");
            }
        }

        List<TrafficIncidentDto> incidents =
                trafficIncidentService.getIncidents(latitude, longitude, radiusKm);
        return ResponseEntity.ok(incidents);
    }
}
