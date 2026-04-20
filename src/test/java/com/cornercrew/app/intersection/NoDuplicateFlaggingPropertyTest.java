package com.cornercrew.app.intersection;

import com.cornercrew.app.campaign.CampaignService;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 11: No Duplicate Flagging (Idempotency)
 *
 * For any FLAGGED intersection, repeated calls to flagIfNotAlready produce no state changes.
 *
 * <p><b>Validates: Requirements 10.3</b></p>
 */
class NoDuplicateFlaggingPropertyTest {

    @Property(tries = 20)
    void flagIfNotAlready_onFlaggedIntersection_isIdempotent(
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("callCounts") int callCount
    ) {
        // --- Set up intersection with FLAGGED status ---
        Intersection intersection = createIntersection(IntersectionStatus.FLAGGED);
        intersection.setId(intersectionId);

        // Capture initial state
        IntersectionStatus initialStatus = intersection.getStatus();
        Double initialCongestionScore = intersection.getCongestionScore();
        OffsetDateTime initialLastCheckedAt = intersection.getLastCheckedAt();

        // --- Mock repositories ---
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CampaignService campaignService = mock(CampaignService.class);

        when(intersectionRepository.findById(intersectionId)).thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        IntersectionCandidateServiceImpl service = new IntersectionCandidateServiceImpl(
                intersectionRepository, campaignService);

        // --- Execute flagIfNotAlready multiple times ---
        for (int i = 0; i < callCount; i++) {
            service.flagIfNotAlready(intersectionId);
        }

        // --- Verify idempotency: no state changes ---
        assertThat(intersection.getStatus())
                .as("Status should remain FLAGGED after %d calls", callCount)
                .isEqualTo(initialStatus)
                .isEqualTo(IntersectionStatus.FLAGGED);

        assertThat(intersection.getCongestionScore())
                .as("Congestion score should not change")
                .isEqualTo(initialCongestionScore);

        assertThat(intersection.getLastCheckedAt())
                .as("Last checked timestamp should not change")
                .isEqualTo(initialLastCheckedAt);

        // --- Verify no repository saves occurred ---
        verify(intersectionRepository, never()).save(any(Intersection.class));

        // --- Verify repository was queried exactly callCount times ---
        verify(intersectionRepository, times(callCount)).findById(intersectionId);
    }

    @Property(tries = 20)
    void flagIfNotAlready_onConfirmedIntersection_isIdempotent(
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("callCounts") int callCount
    ) {
        // --- Set up intersection with CONFIRMED status ---
        Intersection intersection = createIntersection(IntersectionStatus.CONFIRMED);
        intersection.setId(intersectionId);

        IntersectionStatus initialStatus = intersection.getStatus();

        // --- Mock repositories ---
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CampaignService campaignService = mock(CampaignService.class);

        when(intersectionRepository.findById(intersectionId)).thenReturn(Optional.of(intersection));

        IntersectionCandidateServiceImpl service = new IntersectionCandidateServiceImpl(
                intersectionRepository, campaignService);

        // --- Execute flagIfNotAlready multiple times ---
        for (int i = 0; i < callCount; i++) {
            service.flagIfNotAlready(intersectionId);
        }

        // --- Verify status remains CONFIRMED ---
        assertThat(intersection.getStatus())
                .as("Status should remain CONFIRMED after %d calls", callCount)
                .isEqualTo(initialStatus)
                .isEqualTo(IntersectionStatus.CONFIRMED);

        // --- Verify no repository saves occurred ---
        verify(intersectionRepository, never()).save(any(Intersection.class));
    }

    @Property(tries = 20)
    void flagIfNotAlready_onDismissedIntersection_isIdempotent(
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("callCounts") int callCount
    ) {
        // --- Set up intersection with DISMISSED status ---
        Intersection intersection = createIntersection(IntersectionStatus.DISMISSED);
        intersection.setId(intersectionId);

        IntersectionStatus initialStatus = intersection.getStatus();

        // --- Mock repositories ---
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CampaignService campaignService = mock(CampaignService.class);

        when(intersectionRepository.findById(intersectionId)).thenReturn(Optional.of(intersection));

        IntersectionCandidateServiceImpl service = new IntersectionCandidateServiceImpl(
                intersectionRepository, campaignService);

        // --- Execute flagIfNotAlready multiple times ---
        for (int i = 0; i < callCount; i++) {
            service.flagIfNotAlready(intersectionId);
        }

        // --- Verify status remains DISMISSED ---
        assertThat(intersection.getStatus())
                .as("Status should remain DISMISSED after %d calls", callCount)
                .isEqualTo(initialStatus)
                .isEqualTo(IntersectionStatus.DISMISSED);

        // --- Verify no repository saves occurred ---
        verify(intersectionRepository, never()).save(any(Intersection.class));
    }

    @Property(tries = 20)
    void flagIfNotAlready_multipleCalls_neverSavesMoreThanOnce(
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("callCounts") int callCount
    ) {
        // --- Set up intersection with CANDIDATE status (will transition to FLAGGED on first call) ---
        Intersection intersection = createIntersection(IntersectionStatus.CANDIDATE);
        intersection.setId(intersectionId);

        // --- Mock repositories ---
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CampaignService campaignService = mock(CampaignService.class);

        when(intersectionRepository.findById(intersectionId)).thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        IntersectionCandidateServiceImpl service = new IntersectionCandidateServiceImpl(
                intersectionRepository, campaignService);

        // --- Execute flagIfNotAlready multiple times ---
        for (int i = 0; i < callCount; i++) {
            service.flagIfNotAlready(intersectionId);
        }

        // --- Verify status transitioned to FLAGGED ---
        assertThat(intersection.getStatus()).isEqualTo(IntersectionStatus.FLAGGED);

        // --- Verify repository was saved exactly once (on first call only) ---
        verify(intersectionRepository, times(1)).save(intersection);

        // --- Verify repository was queried exactly callCount times ---
        verify(intersectionRepository, times(callCount)).findById(intersectionId);
    }

    @Provide
    Arbitrary<Long> intersectionIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<Integer> callCounts() {
        return Arbitraries.integers().between(2, 10);
    }

    private Intersection createIntersection(IntersectionStatus status) {
        Intersection intersection = new Intersection();
        intersection.setLabel("Test Intersection");
        intersection.setDescription("Test intersection for idempotency testing");
        intersection.setLatitude(37.7749);
        intersection.setLongitude(-122.4194);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(status);
        intersection.setCongestionScore(0.75);
        intersection.setLastCheckedAt(OffsetDateTime.now());
        return intersection;
    }
}
