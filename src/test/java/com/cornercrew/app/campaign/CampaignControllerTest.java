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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

@WebMvcTest(CampaignController.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CampaignService campaignService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private CampaignDto campaignDto;
    private CreateCampaignRequest createRequest;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        createRequest = new CreateCampaignRequest(
                "Test Campaign",
                "A test campaign",
                new BigDecimal("1000.00"),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30)
        );

        campaignDto = new CampaignDto(
                1L,
                "Test Campaign",
                "A test campaign",
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                CampaignStatus.OPEN,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30),
                null,
                OffsetDateTime.now()
        );
    }

    // --- POST /campaigns ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCampaign_asAdmin_returnsCreated() throws Exception {
        when(campaignService.createCampaign(any(CreateCampaignRequest.class), any()))
                .thenReturn(campaignDto);

        mockMvc.perform(post("/campaigns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Campaign"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.targetAmount").value(1000.00))
                .andExpect(jsonPath("$.currentAmount").value(0));
    }

    @Test
    void createCampaign_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/campaigns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCampaign_invalidRequest_returnsBadRequest() throws Exception {
        CreateCampaignRequest invalidRequest = new CreateCampaignRequest(
                "", // blank title
                "desc",
                new BigDecimal("1000.00"),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30)
        );

        mockMvc.perform(post("/campaigns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // --- GET /campaigns ---

    @Test
    @WithMockUser
    void listCampaigns_noStatusFilter_returnsAllCampaigns() throws Exception {
        Page<CampaignDto> page = new PageImpl<>(List.of(campaignDto));
        when(campaignService.listCampaigns(isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/campaigns")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Test Campaign"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void listCampaigns_withStatusFilter_returnsFilteredCampaigns() throws Exception {
        CampaignDto fundedCampaign = new CampaignDto(
                2L,
                "Funded Campaign",
                "desc",
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                CampaignStatus.FUNDED,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        Page<CampaignDto> page = new PageImpl<>(List.of(fundedCampaign));
        when(campaignService.listCampaigns(eq(CampaignStatus.FUNDED), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/campaigns")
                        .param("status", "FUNDED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("FUNDED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listCampaigns_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /campaigns/{id} ---

    @Test
    @WithMockUser
    void getCampaign_existingId_returnsCampaign() throws Exception {
        when(campaignService.getCampaign(1L)).thenReturn(campaignDto);

        mockMvc.perform(get("/campaigns/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Campaign"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getCampaign_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/campaigns/1"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /campaigns/{id}/approve ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveCampaign_asAdmin_returnsApprovedCampaign() throws Exception {
        when(campaignService.approveCampaign(1L)).thenReturn(campaignDto);

        mockMvc.perform(post("/campaigns/1/approve")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void approveCampaign_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/campaigns/1/approve")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
