package com.cornercrew.app.commuteprofile;

import com.cornercrew.app.common.InvalidTimeWindowException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@Transactional
public class CommuteProfileServiceImpl implements CommuteProfileService {

    private final CommuteProfileRepository commuteProfileRepository;

    public CommuteProfileServiceImpl(CommuteProfileRepository commuteProfileRepository) {
        this.commuteProfileRepository = commuteProfileRepository;
    }

    @Override
    public SaveResult saveProfile(Long driverId, SaveCommuteProfileRequest req) {
        if (!req.departureStartTime().isBefore(req.departureEndTime())) {
            throw new InvalidTimeWindowException(
                    "departureStartTime must be before departureEndTime");
        }

        Optional<CommuteProfile> existing = commuteProfileRepository.findByDriverId(driverId);

        if (existing.isPresent()) {
            CommuteProfile profile = existing.get();
            profile.setOriginLatitude(req.originLatitude());
            profile.setOriginLongitude(req.originLongitude());
            profile.setDestinationLatitude(req.destinationLatitude());
            profile.setDestinationLongitude(req.destinationLongitude());
            profile.setDepartureStartTime(req.departureStartTime());
            profile.setDepartureEndTime(req.departureEndTime());
            profile.setUpdatedAt(OffsetDateTime.now());

            CommuteProfile saved = commuteProfileRepository.save(profile);
            return new SaveResult(toDto(saved), false);
        }

        CommuteProfile profile = new CommuteProfile();
        profile.setDriverId(driverId);
        profile.setOriginLatitude(req.originLatitude());
        profile.setOriginLongitude(req.originLongitude());
        profile.setDestinationLatitude(req.destinationLatitude());
        profile.setDestinationLongitude(req.destinationLongitude());
        profile.setDepartureStartTime(req.departureStartTime());
        profile.setDepartureEndTime(req.departureEndTime());
        profile.setCreatedAt(OffsetDateTime.now());

        CommuteProfile saved = commuteProfileRepository.save(profile);
        return new SaveResult(toDto(saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommuteProfileDto> getProfile(Long driverId) {
        return commuteProfileRepository.findByDriverId(driverId).map(this::toDto);
    }

    @Override
    public void deleteProfile(Long driverId) {
        commuteProfileRepository.deleteByDriverId(driverId);
    }

    private CommuteProfileDto toDto(CommuteProfile entity) {
        return new CommuteProfileDto(
                entity.getId(),
                entity.getDriverId(),
                entity.getOriginLatitude(),
                entity.getOriginLongitude(),
                entity.getDestinationLatitude(),
                entity.getDestinationLongitude(),
                entity.getDepartureStartTime(),
                entity.getDepartureEndTime(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
