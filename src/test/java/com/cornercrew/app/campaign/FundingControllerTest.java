package com.cornercrew.app.campaign;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FundingController.class)
class FundingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FundingService fundingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private ContributionDto contributionDto;
    private ContributeRequest contributeRequest;
    private FundingSummaryDto fundingSummaryDto;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        contributeRequest = new ContributeRequest(
                new BigDecimal("50.00"),
                ContributionPeriod.MONTH
        );

        contributionDto = new ContributionDto(
                1L,
                10L,
                5L,
                new BigDecimal("50.00"),
                ContributionPeriod.MONTH,
                OffsetDateTime.now()
        );

        fundingSummaryDto = new FundingSummaryDto(
                new BigDecimal("250.00"),
                new BigDecimal("750.00")
        );
    }

    // --- POST /campaigns/{id}/contributions ---

    @Test
    @WithMockUser(roles = "DRIVER")
    void contribute_asDriver_returnsCreated() throws Exception {
        when(fundingService.contribute(eq(10L), any(), any(ContributeRequest.class)))
                .thenReturn(contributionDto);

        mockMvc.perform(post("/campaigns/10/contributions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contributeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.campaignId").value(10))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.period").value("MONTH"));
    }

    @Test
    void contribute_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/campaigns/10/contributions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contributeRequest)))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /campaigns/{id}/contributions/summary ---

    @Test
    @WithMockUser
    void getSummary_authenticated_returnsOk() throws Exception {
        when(fundingService.getSummary(10L)).thenReturn(fundingSummaryDto);

        mockMvc.perform(get("/campaigns/10/contributions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTotal").value(250.00))
                .andExpect(jsonPath("$.remainingCapacity").value(750.00));
    }

    @Test
    void getSummary_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/campaigns/10/contributions/summary"))
                .andExpect(status().isUnauthorized());
    }
}
