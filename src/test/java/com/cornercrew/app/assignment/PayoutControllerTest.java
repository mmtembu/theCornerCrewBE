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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PayoutController.class)
class PayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayoutService payoutService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private PayoutResultDto payoutResultDto;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        payoutResultDto = new PayoutResultDto(1L, new BigDecimal("250.00"), 4.5);
    }

    // --- POST /assignments/{id}/payout ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void processPayout_asAdmin_returnsOk() throws Exception {
        when(payoutService.processPayout(eq(1L), any()))
                .thenReturn(payoutResultDto);

        mockMvc.perform(post("/assignments/1/payout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(1))
                .andExpect(jsonPath("$.agreedPay").value(250.00))
                .andExpect(jsonPath("$.avgRating").value(4.5));
    }

    @Test
    void processPayout_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/assignments/1/payout")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
