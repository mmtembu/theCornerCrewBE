package com.cornercrew.app.commuteprofile;

import com.cornercrew.app.common.InvalidTimeWindowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommuteProfileServiceImplTest {

    @Mock
    private CommuteProfileRepository commuteProfileRepository;

    @InjectMocks
    private CommuteProfileServiceImpl commuteProfileService;

    // --- deleteProfile tests ---

    @Test
    void deleteProfile_callsDeleteByDriverIdOnRepository() {
        Long driverId = 42L;

        commuteProfileService.deleteProfile(driverId);

        verify(commuteProfileRepository).deleteByDriverId(driverId);
    }

    // --- getProfile tests ---

    @Test
    void getProfile_returnsEmptyOptional_whenNoProfileExists() {
        Long driverId = 99L;
        when(commuteProfileRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        Optional<CommuteProfileDto> result = commuteProfileService.getProfile(driverId);

        assertTrue(result.isEmpty());
        verify(commuteProfileRepository).findByDriverId(driverId);
    }

    // --- saveProfile time-window validation tests ---

    @Test
    void saveProfile_throwsInvalidTimeWindowException_whenStartTimeAfterEndTime() {
        Long driverId = 1L;
        SaveCommuteProfileRequest request = new SaveCommuteProfileRequest(
                37.7749, -122.4194,
                37.3382, -121.8863,
                LocalTime.of(9, 0),   // startTime 09:00
                LocalTime.of(8, 0)    // endTime 08:00 — invalid: start > end
        );

        InvalidTimeWindowException ex = assertThrows(InvalidTimeWindowException.class,
                () -> commuteProfileService.saveProfile(driverId, request));

        assertEquals("departureStartTime must be before departureEndTime", ex.getMessage());
        verify(commuteProfileRepository, never()).save(any());
    }

    @Test
    void saveProfile_throwsInvalidTimeWindowException_whenStartTimeEqualsEndTime() {
        Long driverId = 1L;
        LocalTime sameTime = LocalTime.of(8, 30);
        SaveCommuteProfileRequest request = new SaveCommuteProfileRequest(
                37.7749, -122.4194,
                37.3382, -121.8863,
                sameTime,   // startTime == endTime — invalid
                sameTime
        );

        InvalidTimeWindowException ex = assertThrows(InvalidTimeWindowException.class,
                () -> commuteProfileService.saveProfile(driverId, request));

        assertEquals("departureStartTime must be before departureEndTime", ex.getMessage());
        verify(commuteProfileRepository, never()).save(any());
    }

    // Note: Role enforcement (CONTROLLER/ADMIN users get 403) is handled by
    // @PreAuthorize("hasRole('DRIVER')") on CommuteProfileController, not the service layer.
}
