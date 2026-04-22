package com.cornercrew.app.user;

import java.util.Optional;

public interface UserLocationService {
    void setLocation(Long userId, double latitude, double longitude);
    Optional<UserLocationDto> getLocation(Long userId);
}
