package com.cornercrew.app.geolocation;

import com.cornercrew.app.common.GeoLocationApiUnavailableException;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.intersection.IntersectionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeoLocationServiceImplTest {

    @Mock
    private GeoLocationApiAdapter geoLocationApiAdapter;

    @Mock
    private IntersectionRepository intersectionRepository;

    private MeterRegistry meterRegistry;
    private GeoLocationServiceImpl geoLocationService;

    private BoundingBox testBbox;
    private List<IntersectionCoordinate> testCoordinates;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        geoLocationService = new GeoLocationServiceImpl(
                geoLocationApiAdapter,
                intersectionRepository,
                meterRegistry
        );

        testBbox = new BoundingBox(37.790, -122.425, 37.810, -122.400);
        testCoordinates = List.of(
                new IntersectionCoordinate(37.800, -122.412, "Oak Ave & Main St"),
                new IntersectionCoordinate(37.805, -122.415, "Pine St & 1st Ave")
        );
    }

    @Test
    void resolveIntersections_shouldCallAdapterAndUpsertIntersections() {
        // Given
        when(geoLocationApiAdapter.getIntersections(testBbox)).thenReturn(testCoordinates);
        when(intersectionRepository.findByLatitudeAndLongitude(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        // When
        List<IntersectionCoordinate> result = geoLocationService.resolveIntersections(testBbox);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(testCoordinates);

        verify(geoLocationApiAdapter).getIntersections(testBbox);
        verify(intersectionRepository, times(2)).save(any(Intersection.class));

        // Verify intersections were created with CANDIDATE status
        ArgumentCaptor<Intersection> captor = ArgumentCaptor.forClass(Intersection.class);
        verify(intersectionRepository, times(2)).save(captor.capture());

        List<Intersection> savedIntersections = captor.getAllValues();
        assertThat(savedIntersections).allMatch(i -> i.getStatus() == IntersectionStatus.CANDIDATE);
        assertThat(savedIntersections.get(0).getLabel()).isEqualTo("Oak Ave & Main St");
        assertThat(savedIntersections.get(1).getLabel()).isEqualTo("Pine St & 1st Ave");
    }

    @Test
    void resolveIntersections_shouldUpdateExistingIntersectionLabel() {
        // Given
        Intersection existing = new Intersection();
        existing.setId(1L);
        existing.setLabel("Old Label");
        existing.setLatitude(37.800);
        existing.setLongitude(-122.412);
        existing.setStatus(IntersectionStatus.FLAGGED);

        when(geoLocationApiAdapter.getIntersections(testBbox)).thenReturn(testCoordinates);
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.412))
                .thenReturn(Optional.of(existing));
        when(intersectionRepository.findByLatitudeAndLongitude(37.805, -122.415))
                .thenReturn(Optional.empty());

        // When
        List<IntersectionCoordinate> result = geoLocationService.resolveIntersections(testBbox);

        // Then
        assertThat(result).hasSize(2);
        verify(intersectionRepository, times(2)).save(any(Intersection.class));

        // Verify existing intersection label was updated
        assertThat(existing.getLabel()).isEqualTo("Oak Ave & Main St");
        assertThat(existing.getStatus()).isEqualTo(IntersectionStatus.FLAGGED); // Status unchanged
    }

    @Test
    void resolveIntersections_shouldFilterCoordinatesOutsideBoundingBox() {
        // Given
        List<IntersectionCoordinate> mixedCoordinates = List.of(
                new IntersectionCoordinate(37.800, -122.412, "Inside"),
                new IntersectionCoordinate(37.850, -122.412, "Outside - too far north"),
                new IntersectionCoordinate(37.800, -122.500, "Outside - too far west")
        );

        when(geoLocationApiAdapter.getIntersections(testBbox)).thenReturn(mixedCoordinates);
        when(intersectionRepository.findByLatitudeAndLongitude(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        // When
        List<IntersectionCoordinate> result = geoLocationService.resolveIntersections(testBbox);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("Inside");

        // Only the valid coordinate should be upserted
        verify(intersectionRepository, times(1)).save(any(Intersection.class));
    }

    @Test
    void resolveIntersections_shouldReturnCachedResultOnApiFailure() {
        // Given - first successful call
        when(geoLocationApiAdapter.getIntersections(testBbox)).thenReturn(testCoordinates);
        when(intersectionRepository.findByLatitudeAndLongitude(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        List<IntersectionCoordinate> firstResult = geoLocationService.resolveIntersections(testBbox);
        assertThat(firstResult).hasSize(2);

        // When - second call fails
        when(geoLocationApiAdapter.getIntersections(testBbox))
                .thenThrow(new GeoLocationApiUnavailableException("API unavailable"));

        List<IntersectionCoordinate> secondResult = geoLocationService.resolveIntersections(testBbox);

        // Then - should return cached result
        assertThat(secondResult).hasSize(2);
        assertThat(secondResult).containsExactlyElementsOf(testCoordinates);

        // Verify error counter was incremented
        Counter errorCounter = meterRegistry.find("geolocation.api.errors").counter();
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }

    @Test
    void resolveIntersections_shouldThrowWhenApiFailsAndNoCachedResult() {
        // Given
        when(geoLocationApiAdapter.getIntersections(testBbox))
                .thenThrow(new GeoLocationApiUnavailableException("API unavailable"));

        // When / Then
        assertThatThrownBy(() -> geoLocationService.resolveIntersections(testBbox))
                .isInstanceOf(GeoLocationApiUnavailableException.class)
                .hasMessageContaining("no cached result available");

        // Verify error counter was incremented
        Counter errorCounter = meterRegistry.find("geolocation.api.errors").counter();
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }

    @Test
    void resolveIntersections_shouldNotReturnCachedResultForDifferentBbox() {
        // Given - first successful call with testBbox
        when(geoLocationApiAdapter.getIntersections(testBbox)).thenReturn(testCoordinates);
        when(intersectionRepository.findByLatitudeAndLongitude(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        geoLocationService.resolveIntersections(testBbox);

        // When - call with different bbox fails
        BoundingBox differentBbox = new BoundingBox(37.700, -122.500, 37.720, -122.480);
        when(geoLocationApiAdapter.getIntersections(differentBbox))
                .thenThrow(new GeoLocationApiUnavailableException("API unavailable"));

        // Then - should throw because cached result is for different bbox
        assertThatThrownBy(() -> geoLocationService.resolveIntersections(differentBbox))
                .isInstanceOf(GeoLocationApiUnavailableException.class);
    }

    @Test
    void resolveAreaBoundingBox_shouldCallAdapter() {
        // Given
        String areaName = "Downtown Oakland";
        BoundingBox expectedBbox = new BoundingBox(37.790, -122.425, 37.810, -122.400);
        when(geoLocationApiAdapter.geocodeArea(areaName)).thenReturn(expectedBbox);

        // When
        BoundingBox result = geoLocationService.resolveAreaBoundingBox(areaName);

        // Then
        assertThat(result).isEqualTo(expectedBbox);
        verify(geoLocationApiAdapter).geocodeArea(areaName);
    }

    @Test
    void resolveAreaBoundingBox_shouldIncrementErrorCounterOnFailure() {
        // Given
        String areaName = "Downtown Oakland";
        when(geoLocationApiAdapter.geocodeArea(areaName))
                .thenThrow(new GeoLocationApiUnavailableException("API unavailable"));

        // When / Then
        assertThatThrownBy(() -> geoLocationService.resolveAreaBoundingBox(areaName))
                .isInstanceOf(GeoLocationApiUnavailableException.class);

        // Verify error counter was incremented
        Counter errorCounter = meterRegistry.find("geolocation.api.errors").counter();
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }
}
