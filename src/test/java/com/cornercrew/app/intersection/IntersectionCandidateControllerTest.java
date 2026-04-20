package com.cornercrew.app.intersection;

import com.cornercrew.app.auth.JwtAuthFilter;
import com.cornercrew.app.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntersectionCandidateController.class)
class IntersectionCandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntersectionCandidateService intersectionCandidateService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private IntersectionCandidateDto candidateDto;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        candidateDto = new IntersectionCandidateDto(
                1L,
                "Oak Ave & Main St",
                "High congestion intersection",
                37.800,
                -122.412,
                IntersectionType.FOUR_WAY_STOP,
                IntersectionStatus.FLAGGED,
                0.85,
                OffsetDateTime.now()
        );
    }

    // --- GET /intersections/candidates ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void listCandidates_asAdmin_returnsOk() throws Exception {
        Page<IntersectionCandidateDto> page = new PageImpl<>(List.of(candidateDto));
        when(intersectionCandidateService.listByStatus(eq(IntersectionStatus.FLAGGED), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/intersections/candidates")
                        .param("status", "FLAGGED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].label").value("Oak Ave & Main St"))
                .andExpect(jsonPath("$.content[0].status").value("FLAGGED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listCandidates_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/intersections/candidates"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /intersections/candidates/{id}/confirm ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirm_asAdmin_returnsOk() throws Exception {
        IntersectionCandidateDto confirmedDto = new IntersectionCandidateDto(
                1L, "Oak Ave & Main St", "High congestion intersection",
                37.800, -122.412, IntersectionType.FOUR_WAY_STOP,
                IntersectionStatus.CONFIRMED, 0.85, OffsetDateTime.now()
        );
        when(intersectionCandidateService.confirm(eq(1L), any()))
                .thenReturn(confirmedDto);

        mockMvc.perform(post("/intersections/candidates/1/confirm")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirm_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/intersections/candidates/1/confirm")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /intersections/candidates/{id}/dismiss ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void dismiss_asAdmin_returnsOk() throws Exception {
        IntersectionCandidateDto dismissedDto = new IntersectionCandidateDto(
                1L, "Oak Ave & Main St", "High congestion intersection",
                37.800, -122.412, IntersectionType.FOUR_WAY_STOP,
                IntersectionStatus.DISMISSED, 0.85, OffsetDateTime.now()
        );
        when(intersectionCandidateService.dismiss(eq(1L), any()))
                .thenReturn(dismissedDto);

        mockMvc.perform(post("/intersections/candidates/1/dismiss")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void dismiss_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/intersections/candidates/1/dismiss")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
