package com.cornercrew.app.geolocation;

import com.cornercrew.app.intersection.IntersectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Property 15: Geolocation Cache Hit Avoids Adapter Call
 *
 * Within cache TTL, GeoLocationApiAdapter must not be invoked and cached result is returned.
 *
 * <p><b>Validates: Requirements 9.2</b></p>
 */
@SpringBootTest
@ContextConfiguration(classes = {
        GeoLocationServiceImpl.class,
        GeolocationCacheHitPropertyTest.TestConfig.class
})
class GeolocationCacheHitPropertyTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("intersections");
        }

        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private GeoLocationService geoLocationService;

    @MockBean
    private GeoLocationApiAdapter geoLocationApiAdapter;

    @MockBean
    private IntersectionRepository intersectionRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        if (cacheManager.getCache("intersections") != null) {
            cacheManager.getCache("intersections").clear();
        }
        reset(geoLocationApiAdapter, intersectionRepository);
    }

    @Test
    void withinCacheTTL_adapterNotInvoked_cachedResultReturned() {
        // Create test bounding box
        BoundingBox bbox = new BoundingBox(37.750, -122.450, 37.770, -122.425);

        // Create test coordinates within the bounding box
        List<IntersectionCoordinate> testCoordinates = List.of(
                new IntersectionCoordinate(37.751, -122.449, "Test Intersection 1"),
                new IntersectionCoordinate(37.752, -122.448, "Test Intersection 2")
        );

        when(geoLocationApiAdapter.getIntersections(bbox)).thenReturn(testCoordinates);
        when(intersectionRepository.findByLatitudeAndLongitude(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        // --- First call: should invoke adapter (cache miss) ---
        List<IntersectionCoordinate> firstResult = geoLocationService.resolveIntersections(bbox);
        
        assertThat(firstResult).isNotNull();
        assertThat(firstResult).hasSize(2);
        assertThat(firstResult).containsExactlyElementsOf(testCoordinates);
        
        // Verify adapter was called on first invocation
        verify(geoLocationApiAdapter, times(1)).getIntersections(bbox);

        // --- Subsequent calls with same bbox: should use cache ---
        for (int i = 0; i < 5; i++) {
            List<IntersectionCoordinate> cachedResult = geoLocationService.resolveIntersections(bbox);
            
            // Cached result must match first result
            assertThat(cachedResult)
                    .as("Call %d: cached result must match first result", i + 2)
                    .isNotNull()
                    .hasSize(2)
                    .containsExactlyElementsOf(firstResult);
        }

        // PROPERTY: Adapter must be called exactly once for all calls with same bbox
        // (first call = cache miss, subsequent calls = cache hits)
        verify(geoLocationApiAdapter, times(1)).getIntersections(bbox);
        
        // Verify no additional adapter calls were made
        verifyNoMoreInteractions(geoLocationApiAdapter);
    }
}
