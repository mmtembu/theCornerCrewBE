package com.cornercrew.app.intersection;

import com.cornercrew.app.campaign.CampaignDto;
import com.cornercrew.app.campaign.CampaignService;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.common.InvalidStatusTransitionException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntersectionCandidateServiceImplTest {

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private CampaignService campaignService;

    @InjectMocks
    private IntersectionCandidateServiceImpl intersectionCandidateService;

    // --- flagIfNotAlready tests ---

    @Test
    void flagIfNotAlready_candidateStatus_transitionsToFlagged() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.CANDIDATE);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class))).thenAnswer(i -> i.getArgument(0));

        intersectionCandidateService.flagIfNotAlready(1L);

        assertEquals(IntersectionStatus.FLAGGED, intersection.getStatus());
        verify(intersectionRepository).save(intersection);
    }

    @Test
    void flagIfNotAlready_alreadyFlagged_noOp() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.FLAGGED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        intersectionCandidateService.flagIfNotAlready(1L);

        assertEquals(IntersectionStatus.FLAGGED, intersection.getStatus());
        verify(intersectionRepository, never()).save(any());
    }

    @Test
    void flagIfNotAlready_confirmedStatus_noOp() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.CONFIRMED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        intersectionCandidateService.flagIfNotAlready(1L);

        assertEquals(IntersectionStatus.CONFIRMED, intersection.getStatus());
        verify(intersectionRepository, never()).save(any());
    }

    @Test
    void flagIfNotAlready_dismissedStatus_noOp() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.DISMISSED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        intersectionCandidateService.flagIfNotAlready(1L);

        assertEquals(IntersectionStatus.DISMISSED, intersection.getStatus());
        verify(intersectionRepository, never()).save(any());
    }

    @Test
    void flagIfNotAlready_nonExistingId_throwsEntityNotFound() {
        when(intersectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> intersectionCandidateService.flagIfNotAlready(99L));
    }

    // --- confirm tests ---

    @Test
    void confirm_flaggedIntersection_transitionsToConfirmedAndAutoProposeCampaign() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.FLAGGED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class))).thenAnswer(i -> i.getArgument(0));
        
        CampaignDto mockCampaign = new CampaignDto(
                10L, "Test Campaign", "desc", new BigDecimal("5000.00"),
                BigDecimal.ZERO, CampaignStatus.DRAFT,
                LocalDate.now(), LocalDate.now().plusDays(30),
                null, OffsetDateTime.now()
        );
        when(campaignService.autoProposeCampaign(1L, 100L)).thenReturn(mockCampaign);

        IntersectionCandidateDto result = intersectionCandidateService.confirm(1L, 100L);

        assertEquals(IntersectionStatus.CONFIRMED, result.status());
        verify(intersectionRepository).save(intersection);
        verify(campaignService).autoProposeCampaign(1L, 100L);
    }

    @Test
    void confirm_candidateStatus_throwsInvalidStatusTransition() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.CANDIDATE);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> intersectionCandidateService.confirm(1L, 100L));

        assertEquals("CANDIDATE", ex.getCurrentStatus());
        assertEquals("CONFIRMED", ex.getAttemptedStatus());
        verify(intersectionRepository, never()).save(any());
        verify(campaignService, never()).autoProposeCampaign(any(), any());
    }

    @Test
    void confirm_confirmedStatus_throwsInvalidStatusTransition() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.CONFIRMED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> intersectionCandidateService.confirm(1L, 100L));

        assertEquals("CONFIRMED", ex.getCurrentStatus());
        assertEquals("CONFIRMED", ex.getAttemptedStatus());
        verify(intersectionRepository, never()).save(any());
        verify(campaignService, never()).autoProposeCampaign(any(), any());
    }

    @Test
    void confirm_dismissedStatus_throwsInvalidStatusTransition() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.DISMISSED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> intersectionCandidateService.confirm(1L, 100L));

        assertEquals("DISMISSED", ex.getCurrentStatus());
        assertEquals("CONFIRMED", ex.getAttemptedStatus());
        verify(intersectionRepository, never()).save(any());
        verify(campaignService, never()).autoProposeCampaign(any(), any());
    }

    @Test
    void confirm_nonExistingId_throwsEntityNotFound() {
        when(intersectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> intersectionCandidateService.confirm(99L, 100L));
    }

    // --- dismiss tests ---

    @Test
    void dismiss_flaggedIntersection_transitionsToDismissed() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.FLAGGED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class))).thenAnswer(i -> i.getArgument(0));

        IntersectionCandidateDto result = intersectionCandidateService.dismiss(1L, 100L);

        assertEquals(IntersectionStatus.DISMISSED, result.status());
        verify(intersectionRepository).save(intersection);
    }

    @Test
    void dismiss_candidateStatus_throwsInvalidStatusTransition() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.CANDIDATE);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> intersectionCandidateService.dismiss(1L, 100L));

        assertEquals("CANDIDATE", ex.getCurrentStatus());
        assertEquals("DISMISSED", ex.getAttemptedStatus());
        verify(intersectionRepository, never()).save(any());
    }

    @Test
    void dismiss_confirmedStatus_throwsInvalidStatusTransition() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.CONFIRMED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> intersectionCandidateService.dismiss(1L, 100L));

        assertEquals("CONFIRMED", ex.getCurrentStatus());
        assertEquals("DISMISSED", ex.getAttemptedStatus());
        verify(intersectionRepository, never()).save(any());
    }

    @Test
    void dismiss_dismissedStatus_throwsInvalidStatusTransition() {
        Intersection intersection = buildIntersection(1L, IntersectionStatus.DISMISSED);
        when(intersectionRepository.findById(1L)).thenReturn(Optional.of(intersection));

        InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                () -> intersectionCandidateService.dismiss(1L, 100L));

        assertEquals("DISMISSED", ex.getCurrentStatus());
        assertEquals("DISMISSED", ex.getAttemptedStatus());
        verify(intersectionRepository, never()).save(any());
    }

    @Test
    void dismiss_nonExistingId_throwsEntityNotFound() {
        when(intersectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> intersectionCandidateService.dismiss(99L, 100L));
    }

    // --- listByStatus tests ---

    @Test
    void listByStatus_flaggedStatus_returnsFlaggedIntersections() {
        Pageable pageable = PageRequest.of(0, 10);
        Intersection i1 = buildIntersection(1L, IntersectionStatus.FLAGGED);
        Intersection i2 = buildIntersection(2L, IntersectionStatus.FLAGGED);
        when(intersectionRepository.findByStatus(IntersectionStatus.FLAGGED, pageable))
                .thenReturn(new PageImpl<>(List.of(i1, i2)));

        Page<IntersectionCandidateDto> result = intersectionCandidateService.listByStatus(
                IntersectionStatus.FLAGGED, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals(IntersectionStatus.FLAGGED, result.getContent().get(0).status());
        assertEquals(IntersectionStatus.FLAGGED, result.getContent().get(1).status());
    }

    @Test
    void listByStatus_confirmedStatus_returnsConfirmedIntersections() {
        Pageable pageable = PageRequest.of(0, 10);
        Intersection i = buildIntersection(1L, IntersectionStatus.CONFIRMED);
        when(intersectionRepository.findByStatus(IntersectionStatus.CONFIRMED, pageable))
                .thenReturn(new PageImpl<>(List.of(i)));

        Page<IntersectionCandidateDto> result = intersectionCandidateService.listByStatus(
                IntersectionStatus.CONFIRMED, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(IntersectionStatus.CONFIRMED, result.getContent().get(0).status());
    }

    @Test
    void listByStatus_noMatches_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(intersectionRepository.findByStatus(IntersectionStatus.DISMISSED, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<IntersectionCandidateDto> result = intersectionCandidateService.listByStatus(
                IntersectionStatus.DISMISSED, pageable);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    // --- helper ---

    private Intersection buildIntersection(Long id, IntersectionStatus status) {
        Intersection i = new Intersection();
        i.setId(id);
        i.setLabel("Oak Ave & Main St");
        i.setDescription("Test intersection");
        i.setLatitude(37.8);
        i.setLongitude(-122.4);
        i.setType(IntersectionType.FOUR_WAY_STOP);
        i.setStatus(status);
        i.setCongestionScore(0.75);
        i.setLastCheckedAt(OffsetDateTime.now());
        return i;
    }
}
