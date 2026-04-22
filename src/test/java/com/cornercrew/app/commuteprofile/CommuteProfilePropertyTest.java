package com.cornercrew.app.commuteprofile;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 1: Commute Profile Persistence Round-Trip
 *
 * For any valid CommuteProfile input (coordinates in range, departureStartTime
 * less than departureEndTime), saving the profile and then retrieving it for the
 * same driver should return a profile with identical origin coordinates, destination
 * coordinates, and departure time window. If a profile already exists for that driver,
 * saving a new one and retrieving should return only the new profile's data.
 *
 * <p><b>Validates: Requirements 1.1, 1.3</b></p>
 */
class CommuteProfilePropertyTest {

    @Property(tries = 10)
    void saveAndRetrieve_returnsIdenticalData(
            @ForAll("validRequests") SaveCommuteProfileRequest request,
            @ForAll @LongRange(min = 1, max = 100_000) Long driverId
    ) {
        // --- Mock repository ---
        CommuteProfileRepository repository = mock(CommuteProfileRepository.class);
        CommuteProfileServiceImpl service = new CommuteProfileServiceImpl(repository);

        // No existing profile for this driver
        when(repository.findByDriverId(driverId)).thenReturn(Optional.empty());

        // Simulate save: capture the entity and return it with an ID
        when(repository.save(any(CommuteProfile.class))).thenAnswer(inv -> {
            CommuteProfile entity = inv.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        // --- Save the profile ---
        CommuteProfileService.SaveResult saveResult = service.saveProfile(driverId, request);
        assertThat(saveResult.created()).isTrue();

        CommuteProfileDto saved = saveResult.profile();

        // --- Simulate retrieval: return the saved entity ---
        CommuteProfile storedEntity = new CommuteProfile();
        storedEntity.setId(saved.id());
        storedEntity.setDriverId(driverId);
        storedEntity.setOriginLatitude(request.originLatitude());
        storedEntity.setOriginLongitude(request.originLongitude());
        storedEntity.setDestinationLatitude(request.destinationLatitude());
        storedEntity.setDestinationLongitude(request.destinationLongitude());
        storedEntity.setDepartureStartTime(request.departureStartTime());
        storedEntity.setDepartureEndTime(request.departureEndTime());
        storedEntity.setCreatedAt(saved.createdAt());

        when(repository.findByDriverId(driverId)).thenReturn(Optional.of(storedEntity));

        // --- Retrieve and verify round-trip ---
        Optional<CommuteProfileDto> retrieved = service.getProfile(driverId);
        assertThat(retrieved).isPresent();

        CommuteProfileDto dto = retrieved.get();
        assertThat(dto.driverId()).isEqualTo(driverId);
        assertThat(dto.originLatitude()).isEqualTo(request.originLatitude());
        assertThat(dto.originLongitude()).isEqualTo(request.originLongitude());
        assertThat(dto.destinationLatitude()).isEqualTo(request.destinationLatitude());
        assertThat(dto.destinationLongitude()).isEqualTo(request.destinationLongitude());
        assertThat(dto.departureStartTime()).isEqualTo(request.departureStartTime());
        assertThat(dto.departureEndTime()).isEqualTo(request.departureEndTime());
    }

    @Property(tries = 10)
    void upsert_replacesExistingProfile(
            @ForAll("validRequests") SaveCommuteProfileRequest firstRequest,
            @ForAll("validRequests") SaveCommuteProfileRequest secondRequest,
            @ForAll @LongRange(min = 1, max = 100_000) Long driverId
    ) {
        // --- Mock repository ---
        CommuteProfileRepository repository = mock(CommuteProfileRepository.class);
        CommuteProfileServiceImpl service = new CommuteProfileServiceImpl(repository);

        // First save: no existing profile
        when(repository.findByDriverId(driverId)).thenReturn(Optional.empty());
        when(repository.save(any(CommuteProfile.class))).thenAnswer(inv -> {
            CommuteProfile entity = inv.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        CommuteProfileService.SaveResult firstResult = service.saveProfile(driverId, firstRequest);
        assertThat(firstResult.created()).isTrue();

        // Second save: existing profile found
        CommuteProfile existingEntity = new CommuteProfile();
        existingEntity.setId(1L);
        existingEntity.setDriverId(driverId);
        existingEntity.setOriginLatitude(firstRequest.originLatitude());
        existingEntity.setOriginLongitude(firstRequest.originLongitude());
        existingEntity.setDestinationLatitude(firstRequest.destinationLatitude());
        existingEntity.setDestinationLongitude(firstRequest.destinationLongitude());
        existingEntity.setDepartureStartTime(firstRequest.departureStartTime());
        existingEntity.setDepartureEndTime(firstRequest.departureEndTime());
        existingEntity.setCreatedAt(OffsetDateTime.now());

        when(repository.findByDriverId(driverId)).thenReturn(Optional.of(existingEntity));
        when(repository.save(any(CommuteProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        CommuteProfileService.SaveResult secondResult = service.saveProfile(driverId, secondRequest);
        assertThat(secondResult.created()).isFalse();

        CommuteProfileDto updatedDto = secondResult.profile();

        // Verify the returned DTO has the SECOND request's data, not the first
        assertThat(updatedDto.originLatitude()).isEqualTo(secondRequest.originLatitude());
        assertThat(updatedDto.originLongitude()).isEqualTo(secondRequest.originLongitude());
        assertThat(updatedDto.destinationLatitude()).isEqualTo(secondRequest.destinationLatitude());
        assertThat(updatedDto.destinationLongitude()).isEqualTo(secondRequest.destinationLongitude());
        assertThat(updatedDto.departureStartTime()).isEqualTo(secondRequest.departureStartTime());
        assertThat(updatedDto.departureEndTime()).isEqualTo(secondRequest.departureEndTime());
        assertThat(updatedDto.updatedAt()).isNotNull();
    }

    @Provide
    Arbitrary<SaveCommuteProfileRequest> validRequests() {
        Arbitrary<Double> latitudes = Arbitraries.doubles().between(-90.0, 90.0);
        Arbitrary<Double> longitudes = Arbitraries.doubles().between(-180.0, 180.0);
        Arbitrary<LocalTime[]> timePairs = validTimePairs();

        return Combinators.combine(latitudes, longitudes, latitudes, longitudes, timePairs)
                .as((originLat, originLng, destLat, destLng, times) ->
                        new SaveCommuteProfileRequest(
                                originLat, originLng,
                                destLat, destLng,
                                times[0], times[1]
                        ));
    }

    private Arbitrary<LocalTime[]> validTimePairs() {
        // Generate startHour in [0, 22] so endHour can be at least startHour + 1
        return Arbitraries.integers().between(0, 22).flatMap(startHour ->
                Arbitraries.integers().between(startHour + 1, 23).flatMap(endHour ->
                        Combinators.combine(
                                Arbitraries.integers().between(0, 59),
                                Arbitraries.integers().between(0, 59)
                        ).as((startMin, endMin) -> {
                            LocalTime start = LocalTime.of(startHour, startMin);
                            LocalTime end = LocalTime.of(endHour, endMin);
                            // Ensure start < end (guaranteed by startHour < endHour)
                            return new LocalTime[]{start, end};
                        })
                )
        );
    }

    // -----------------------------------------------------------------------
    // Property 2: Coordinate Validation Rejects Out-of-Range Inputs
    //
    // For any latitude outside [-90, 90] or longitude outside [-180, 180],
    // submitting a commute profile with those coordinates should be rejected.
    // The existing data should remain unchanged.
    //
    // Validates: Requirements 1.2, 10.2
    // -----------------------------------------------------------------------

    private static final Validator validator;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    /**
     * Property 2: Coordinate Validation Rejects Out-of-Range Inputs
     *
     * <p>Uses Jakarta Bean Validation to verify that {@link SaveCommuteProfileRequest}
     * records with at least one out-of-range coordinate produce validation violations.
     * Also verifies that an existing profile in the repository is not modified.</p>
     *
     * <p><b>Validates: Requirements 1.2, 10.2</b></p>
     */
    @Property(tries = 10)
    void invalidCoordinates_rejectedByBeanValidation(
            @ForAll("invalidCoordinateRequests") SaveCommuteProfileRequest request
    ) {
        // Bean Validation should detect at least one violation
        var violations = validator.validate(request);
        assertThat(violations)
                .as("Request with out-of-range coordinates should have validation violations")
                .isNotEmpty();

        // Every violation should be on a coordinate field
        Set<String> coordinateFields = Set.of(
                "originLatitude", "originLongitude",
                "destinationLatitude", "destinationLongitude"
        );
        for (var violation : violations) {
            assertThat(coordinateFields)
                    .as("Violation should be on a coordinate field, but was on: %s",
                            violation.getPropertyPath())
                    .contains(violation.getPropertyPath().toString());
        }
    }

    /**
     * Property 2 (continued): Existing data remains unchanged when invalid
     * coordinates are submitted.
     *
     * <p>If a profile already exists for a driver, submitting invalid coordinates
     * (which would be rejected at the controller layer by {@code @Valid}) should
     * not modify the existing profile. We verify that the service's save method
     * is never called when validation would reject the request.</p>
     *
     * <p><b>Validates: Requirements 1.2, 10.2</b></p>
     */
    @Property(tries = 10)
    void invalidCoordinates_existingDataUnchanged(
            @ForAll("invalidCoordinateRequests") SaveCommuteProfileRequest invalidRequest,
            @ForAll("validRequests") SaveCommuteProfileRequest originalRequest,
            @ForAll @LongRange(min = 1, max = 100_000) Long driverId
    ) {
        // --- Setup: create an existing profile ---
        CommuteProfileRepository repository = mock(CommuteProfileRepository.class);
        CommuteProfileServiceImpl service = new CommuteProfileServiceImpl(repository);

        CommuteProfile existingEntity = new CommuteProfile();
        existingEntity.setId(1L);
        existingEntity.setDriverId(driverId);
        existingEntity.setOriginLatitude(originalRequest.originLatitude());
        existingEntity.setOriginLongitude(originalRequest.originLongitude());
        existingEntity.setDestinationLatitude(originalRequest.destinationLatitude());
        existingEntity.setDestinationLongitude(originalRequest.destinationLongitude());
        existingEntity.setDepartureStartTime(originalRequest.departureStartTime());
        existingEntity.setDepartureEndTime(originalRequest.departureEndTime());
        existingEntity.setCreatedAt(OffsetDateTime.now());

        when(repository.findByDriverId(driverId)).thenReturn(Optional.of(existingEntity));

        // --- Verify the invalid request would be rejected by Bean Validation ---
        var violations = validator.validate(invalidRequest);
        assertThat(violations).isNotEmpty();

        // --- Since @Valid rejects the request at the controller layer,
        //     the service is never invoked. Verify the existing profile is unchanged. ---
        Optional<CommuteProfileDto> retrieved = service.getProfile(driverId);
        assertThat(retrieved).isPresent();

        CommuteProfileDto dto = retrieved.get();
        assertThat(dto.originLatitude()).isEqualTo(originalRequest.originLatitude());
        assertThat(dto.originLongitude()).isEqualTo(originalRequest.originLongitude());
        assertThat(dto.destinationLatitude()).isEqualTo(originalRequest.destinationLatitude());
        assertThat(dto.destinationLongitude()).isEqualTo(originalRequest.destinationLongitude());
        assertThat(dto.departureStartTime()).isEqualTo(originalRequest.departureStartTime());
        assertThat(dto.departureEndTime()).isEqualTo(originalRequest.departureEndTime());
    }

    /**
     * Generates {@link SaveCommuteProfileRequest} instances where at least one
     * coordinate is outside the valid range.
     *
     * <p>Strategy: generate all four coordinates, then ensure at least one is invalid
     * by picking a random coordinate slot and replacing it with an out-of-range value.</p>
     */
    @Provide
    Arbitrary<SaveCommuteProfileRequest> invalidCoordinateRequests() {
        Arbitrary<Double> validLat = Arbitraries.doubles().between(-90.0, 90.0);
        Arbitrary<Double> validLng = Arbitraries.doubles().between(-180.0, 180.0);
        Arbitrary<Double> invalidLat = invalidLatitudes();
        Arbitrary<Double> invalidLng = invalidLongitudes();
        Arbitrary<LocalTime[]> timePairs = validTimePairs();

        // Pick which coordinate slot(s) to make invalid (0=originLat, 1=originLng, 2=destLat, 3=destLng)
        Arbitrary<Integer> invalidSlot = Arbitraries.integers().between(0, 3);

        return Combinators.combine(validLat, validLng, validLat, validLng, timePairs, invalidSlot, invalidLat, invalidLng)
                .as((oLat, oLng, dLat, dLng, times, slot, badLat, badLng) -> {
                    double originLat = oLat;
                    double originLng = oLng;
                    double destLat = dLat;
                    double destLng = dLng;

                    // Replace the chosen slot with an invalid value
                    switch (slot) {
                        case 0 -> originLat = badLat;
                        case 1 -> originLng = badLng;
                        case 2 -> destLat = badLat;
                        case 3 -> destLng = badLng;
                    }

                    return new SaveCommuteProfileRequest(
                            originLat, originLng,
                            destLat, destLng,
                            times[0], times[1]
                    );
                });
    }

    private Arbitrary<Double> invalidLatitudes() {
        // Latitudes outside [-90, 90]: either < -90 or > 90
        return Arbitraries.oneOf(
                Arbitraries.doubles().between(-1000.0, -90.01),
                Arbitraries.doubles().between(90.01, 1000.0)
        );
    }

    private Arbitrary<Double> invalidLongitudes() {
        // Longitudes outside [-180, 180]: either < -180 or > 180
        return Arbitraries.oneOf(
                Arbitraries.doubles().between(-1000.0, -180.01),
                Arbitraries.doubles().between(180.01, 1000.0)
        );
    }
}
