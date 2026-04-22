package com.cornercrew.app.incident;

import java.util.List;

public interface TrafficIncidentService {
    List<TrafficIncidentDto> getIncidents(double latitude, double longitude, double radiusKm);
}
