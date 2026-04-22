package com.cornercrew.app.notification;

import com.cornercrew.app.user.User;
import com.cornercrew.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferencesServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationPreferencesServiceImpl notificationPreferencesService;

    private User createUserWithId(Long id) {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user.setName("Test User");
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Test
    void getPreferences_returnsDefaults_forNewUser() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NotificationPreferencesDto result = notificationPreferencesService.getPreferences(userId);

        assertTrue(result.commuteNotificationsEnabled());
        assertTrue(result.jobNotificationsEnabled());
    }

    @Test
    void updatePreferences_withCommuteNotificationsDisabled_updatesOnlyThatField() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateNotificationPreferencesRequest req = new UpdateNotificationPreferencesRequest(false, null);
        NotificationPreferencesDto result = notificationPreferencesService.updatePreferences(userId, req);

        assertFalse(result.commuteNotificationsEnabled());
        assertTrue(result.jobNotificationsEnabled());
        verify(userRepository).save(user);
    }

    @Test
    void updatePreferences_withJobNotificationsDisabled_updatesOnlyThatField() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateNotificationPreferencesRequest req = new UpdateNotificationPreferencesRequest(null, false);
        NotificationPreferencesDto result = notificationPreferencesService.updatePreferences(userId, req);

        assertTrue(result.commuteNotificationsEnabled());
        assertFalse(result.jobNotificationsEnabled());
        verify(userRepository).save(user);
    }

    @Test
    void updatePreferences_withBothFieldsSet_updatesBoth() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateNotificationPreferencesRequest req = new UpdateNotificationPreferencesRequest(false, false);
        NotificationPreferencesDto result = notificationPreferencesService.updatePreferences(userId, req);

        assertFalse(result.commuteNotificationsEnabled());
        assertFalse(result.jobNotificationsEnabled());
        verify(userRepository).save(user);
    }
}
