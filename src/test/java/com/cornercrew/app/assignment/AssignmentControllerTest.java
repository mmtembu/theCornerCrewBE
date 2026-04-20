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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssignmentController.class)
class AssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssignmentService assignmentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private AssignControllerRequest assignRequest;
    private AssignmentDto assignmentDto;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        assignRequest = new AssignControllerRequest(
                10L, 20L,
                List.of(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2)),
                new BigDecimal("250.00")
        );

        assignmentDto = new AssignmentDto(
                1L, 1L, 10L, 20L,
                AssignmentStatus.ASSIGNED,
                new BigDecimal("250.00"),
                OffsetDateTime.now(),
                null
        );
    }

    // --- POST /campaigns/{id}/assignments ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void assign_asAdmin_returnsCreated() throws Exception {
        when(assignmentService.assign(eq(1L), any(AssignControllerRequest.class), any()))
                .thenReturn(assignmentDto);

        mockMvc.perform(post("/campaigns/1/assignments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.campaignId").value(1))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.agreedPay").value(250.00));
    }

    @Test
    void assign_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/campaigns/1/assignments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /campaigns/{id}/assignments ---

    @Test
    @WithMockUser
    void listAssignments_authenticated_returnsOk() throws Exception {
        when(assignmentService.listAssignments(1L))
                .thenReturn(List.of(assignmentDto));

        mockMvc.perform(get("/campaigns/1/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));
    }

    @Test
    void listAssignments_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/campaigns/1/assignments"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /assignments/{id} ---

    @Test
    @WithMockUser
    void getAssignment_authenticated_returnsOk() throws Exception {
        when(assignmentService.getAssignment(1L)).thenReturn(assignmentDto);

        mockMvc.perform(get("/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.controllerId").value(10))
                .andExpect(jsonPath("$.intersectionId").value(20));
    }

    @Test
    void getAssignment_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/assignments/1"))
                .andExpect(status().isUnauthorized());
    }
}
