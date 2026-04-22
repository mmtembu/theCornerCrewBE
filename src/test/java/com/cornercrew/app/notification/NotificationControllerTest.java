package com.cornercrew.app.notification;

import com.cornercrew.app.assignment.ApplicationService;
import com.cornercrew.app.auth.JwtAuthFilter;
import com.cornercrew.app.auth.JwtService;
import com.cornercrew.app.common.NotificationNotFoundException;
import com.cornercrew.app.common.NotificationOwnershipException;
import com.cornercrew.app.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ApplicationService applicationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    private User mockUser;
    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class));

        mockUser = new User();
        mockUser.setEmail("driver@test.com");
        mockUser.setName("Test Driver");
        mockUser.setPasswordHash("hashed");

        // User.id has no setter (JPA-managed), so set via reflection
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(mockUser, 42L);

        authToken = new UsernamePasswordAuthenticationToken(
                mockUser, null, mockUser.getAuthorities());
    }

    // --- PATCH /notifications/{id}/read ---

    @Test
    void markAsRead_returns200() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationDto dto = new NotificationDto(
                1L, 42L, NotificationType.COMMUTE_IMPACT,
                "Test Title", "Test Body", null, null,
                now, now, null);

        when(notificationService.markAsRead(eq(1L), eq(42L)))
                .thenReturn(dto);

        mockMvc.perform(patch("/notifications/1/read")
                        .with(csrf())
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    // --- PATCH /notifications/{id}/dismiss ---

    @Test
    void dismiss_returns200() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationDto dto = new NotificationDto(
                2L, 42L, NotificationType.JOB_AVAILABLE,
                "Job Title", "Job Body", null, "/campaigns/1/applications",
                now, null, now);

        when(notificationService.dismiss(eq(2L), eq(42L)))
                .thenReturn(dto);

        mockMvc.perform(patch("/notifications/2/dismiss")
                        .with(csrf())
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.dismissedAt").isNotEmpty());
    }

    // --- Ownership enforcement ---

    @Test
    void markAsRead_ownershipViolation_returns403() throws Exception {
        when(notificationService.markAsRead(eq(99L), eq(42L)))
                .thenThrow(new NotificationOwnershipException("Not your notification"));

        mockMvc.perform(patch("/notifications/99/read")
                        .with(csrf())
                        .with(authentication(authToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_FORBIDDEN"));
    }

    // --- Notification not found ---

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        when(notificationService.markAsRead(eq(999L), eq(42L)))
                .thenThrow(new NotificationNotFoundException("Notification not found"));

        mockMvc.perform(patch("/notifications/999/read")
                        .with(csrf())
                        .with(authentication(authToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_NOT_FOUND"));
    }
}
