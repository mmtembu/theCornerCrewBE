package com.cornercrew.app.notification;

import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationPreferencesServiceImpl implements NotificationPreferencesService {

    private final UserRepository userRepository;

    public NotificationPreferencesServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public NotificationPreferencesDto updatePreferences(Long userId, UpdateNotificationPreferencesRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (req.commuteNotificationsEnabled() != null) {
            user.setCommuteNotificationsEnabled(req.commuteNotificationsEnabled());
        }
        if (req.jobNotificationsEnabled() != null) {
            user.setJobNotificationsEnabled(req.jobNotificationsEnabled());
        }

        User saved = userRepository.save(user);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesDto getPreferences(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        return toDto(user);
    }

    private NotificationPreferencesDto toDto(User user) {
        return new NotificationPreferencesDto(
                user.isCommuteNotificationsEnabled(),
                user.isJobNotificationsEnabled()
        );
    }
}
