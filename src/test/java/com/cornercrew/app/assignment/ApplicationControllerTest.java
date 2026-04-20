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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApplicationService applicationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private ApplicationDto applicationDto;
    private ApplyRequest applyRequest;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        applyRequest = new ApplyRequest("I have 5 years of experience");

        applicationDto = new ApplicationDto(
                1L, 1L, 10L,
                ApplicationStatus.PENDING,
                "I have 5 years of experience",
                OffsetDateTime.now()
        );
    }

    // --- POST /campaigns/{id}/applications ---

    @Test
    @WithMockUser(roles = "CONTROLLER")
    void apply_asController_returnsCreated() throws Exception {
        when(applicationService.apply(eq(1L), any(), any(ApplyRequest.class)))
                .thenReturn(applicationDto);

        mockMvc.perform(post("/campaigns/1/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.campaignId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void apply_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/campaigns/1/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyRequest)))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /campaigns/{id}/applications ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void listApplications_asAdmin_returnsOk() throws Exception {
        when(applicationService.listApplications(1L))
                .thenReturn(List.of(applicationDto));

        mockMvc.perform(get("/campaigns/1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void listApplications_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/campaigns/1/applications"))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /campaigns/{id}/applications/{appId}/status ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_asAdmin_returnsOk() throws Exception {
        ApplicationDto updatedDto = new ApplicationDto(
                1L, 1L, 10L,
                ApplicationStatus.ACCEPTED,
                "I have 5 years of experience",
                OffsetDateTime.now()
        );
        when(applicationService.updateStatus(eq(1L), eq(ApplicationStatus.ACCEPTED)))
                .thenReturn(updatedDto);

        mockMvc.perform(put("/campaigns/1/applications/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ApplicationStatus.ACCEPTED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void updateStatus_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(put("/campaigns/1/applications/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ApplicationStatus.ACCEPTED)))
                .andExpect(status().isUnauthorized());
    }
}
