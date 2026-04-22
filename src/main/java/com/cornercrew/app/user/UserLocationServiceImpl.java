package com.cornercrew.app.user;

import com.cornercrew.app.common.InvalidCoordinatesException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class UserLocationServiceImpl implements UserLocationService {

    private final UserRepository userRepository;

    public UserLocationServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void setLocation(Long userId, double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new InvalidCoordinatesException(
                    "Coordinates out of range: latitude must be in [-90, 90], longitude must be in [-180, 180]");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        user.setHomeLatitude(latitude);
        user.setHomeLongitude(longitude);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserLocationDto> getLocation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (user.getHomeLatitude() != null && user.getHomeLongitude() != null) {
            return Optional.of(new UserLocationDto(user.getHomeLatitude(), user.getHomeLongitude()));
        }
        return Optional.empty();
    }
}
