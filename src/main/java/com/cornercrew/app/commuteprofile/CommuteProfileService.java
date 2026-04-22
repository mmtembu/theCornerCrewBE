package com.cornercrew.app.commuteprofile;

import java.util.Optional;

public interface CommuteProfileService {

    record SaveResult(CommuteProfileDto profile, boolean created) {}

    SaveResult saveProfile(Long driverId, SaveCommuteProfileRequest req);

    Optional<CommuteProfileDto> getProfile(Long driverId);

    void deleteProfile(Long driverId);
}
