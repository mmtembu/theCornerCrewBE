package com.cornercrew.app.assignment;

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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private SubmitReviewRequest reviewRequest;
    private ReviewDto reviewDto;
    private ReviewSummaryDto reviewSummaryDto;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        reviewRequest = new SubmitReviewRequest(5, "Great job!");

        reviewDto = new ReviewDto(
                1L, 1L, 10L,
                5, "Great job!",
                OffsetDateTime.now()
        );

        reviewSummaryDto = new ReviewSummaryDto(4.5, 3);
    }

    // --- POST /assignments/{id}/reviews ---

    @Test
    @WithMockUser(roles = "DRIVER")
    void submitReview_asDriver_returnsCreated() throws Exception {
        when(reviewService.submitReview(eq(1L), any(), any(SubmitReviewRequest.class)))
                .thenReturn(reviewDto);

        mockMvc.perform(post("/assignments/1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.assignmentId").value(1))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Great job!"));
    }

    @Test
    void submitReview_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/assignments/1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /assignments/{id}/reviews/summary ---

    @Test
    @WithMockUser
    void getSummary_authenticated_returnsOk() throws Exception {
        when(reviewService.getSummary(1L)).thenReturn(reviewSummaryDto);

        mockMvc.perform(get("/assignments/1/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(3));
    }

    @Test
    void getSummary_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/assignments/1/reviews/summary"))
                .andExpect(status().isUnauthorized());
    }
}
