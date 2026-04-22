package com.cornercrew.app.user;

import com.cornercrew.app.common.InvalidCoordinatesException;
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
class UserLocationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserLocationServiceImpl userLocationService;

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
    void setLocation_withValidCoordinates_persistsOnUser() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userLocationService.setLocation(userId, 40.7128, -74.0060);

        assertEquals(40.7128, user.getHomeLatitude());
        assertEquals(-74.0060, user.getHomeLongitude());
        verify(userRepository).save(user);
    }

    @Test
    void getLocation_returnsStoredLocation_whenSet() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        user.setHomeLatitude(37.7749);
        user.setHomeLongitude(-122.4194);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<UserLocationDto> result = userLocationService.getLocation(userId);

        assertTrue(result.isPresent());
        assertEquals(37.7749, result.get().latitude());
        assertEquals(-122.4194, result.get().longitude());
    }

    @Test
    void getLocation_returnsEmpty_whenNoLocationSet() {
        Long userId = 1L;
        User user = createUserWithId(userId);
        // homeLatitude and homeLongitude are null by default
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<UserLocationDto> result = userLocationService.getLocation(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void setLocation_withInvalidLatitude_throwsInvalidCoordinatesException() {
        Long userId = 1L;

        InvalidCoordinatesException ex = assertThrows(InvalidCoordinatesException.class,
                () -> userLocationService.setLocation(userId, 91.0, -74.0060));

        assertTrue(ex.getMessage().contains("Coordinates out of range"));
        verify(userRepository, never()).save(any());
    }
}
