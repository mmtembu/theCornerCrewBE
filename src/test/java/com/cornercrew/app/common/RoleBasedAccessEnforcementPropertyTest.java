package com.cornercrew.app.common;

import com.cornercrew.app.assignment.*;
import com.cornercrew.app.campaign.*;
import com.cornercrew.app.intersection.*;
import com.cornercrew.app.user.Role;
import net.jqwik.api.*;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 16: Role-Based Access Enforcement
 *
 * For any role and any protected service method, only the correct role can invoke it
 * without AccessDeniedException. This test verifies that @PreAuthorize annotations
 * are correctly placed on all role-restricted service methods and that the required
 * role matches the expected role for each method.
 *
 * <p><b>Validates: Requirements 1.4, 1.5, 1.6, 13.1, 13.2</b></p>
 */
class RoleBasedAccessEnforcementPropertyTest {

    /**
     * Defines the expected role restrictions for each protected service method.
     * Key: "ClassName.methodName", Value: expected role in @PreAuthorize annotation.
     */
    private static final Map<String, String> EXPECTED_ROLE_RESTRICTIONS = Map.ofEntries(
            // CampaignService - ADMIN only
            Map.entry("CampaignServiceImpl.createCampaign", "ADMIN"),
            Map.entry("CampaignServiceImpl.approveCampaign", "ADMIN"),
            Map.entry("CampaignServiceImpl.autoProposeCampaign", "ADMIN"),

            // FundingService - DRIVER only
            Map.entry("FundingServiceImpl.contribute", "DRIVER"),

            // ApplicationService - CONTROLLER for apply, ADMIN for admin operations
            Map.entry("ApplicationServiceImpl.apply", "CONTROLLER"),
            Map.entry("ApplicationServiceImpl.listApplications", "ADMIN"),
            Map.entry("ApplicationServiceImpl.updateStatus", "ADMIN"),

            // AssignmentService - ADMIN for assign
            Map.entry("AssignmentServiceImpl.assign", "ADMIN"),

            // ReviewService - DRIVER only
            Map.entry("ReviewServiceImpl.submitReview", "DRIVER"),

            // PayoutService - ADMIN only
            Map.entry("PayoutServiceImpl.processPayout", "ADMIN"),

            // IntersectionCandidateService - ADMIN for confirm/dismiss
            Map.entry("IntersectionCandidateServiceImpl.confirm", "ADMIN"),
            Map.entry("IntersectionCandidateServiceImpl.dismiss", "ADMIN")
    );

    /**
     * All service implementation classes that contain @PreAuthorize annotations.
     */
    private static final List<Class<?>> SERVICE_CLASSES = List.of(
            CampaignServiceImpl.class,
            FundingServiceImpl.class,
            ApplicationServiceImpl.class,
            AssignmentServiceImpl.class,
            ReviewServiceImpl.class,
            PayoutServiceImpl.class,
            IntersectionCandidateServiceImpl.class
    );

    @Property(tries = 20)
    void forAnyRoleAndProtectedMethod_onlyCorrectRoleIsAllowed(
            @ForAll("roles") Role role,
            @ForAll("protectedMethodKeys") String methodKey
    ) {
        String expectedRole = EXPECTED_ROLE_RESTRICTIONS.get(methodKey);
        assertThat(expectedRole)
                .as("Expected role restriction must be defined for method: %s", methodKey)
                .isNotNull();

        // Parse class and method name
        String[] parts = methodKey.split("\\.");
        String className = parts[0];
        String methodName = parts[1];

        // Find the actual class
        Class<?> serviceClass = SERVICE_CLASSES.stream()
                .filter(c -> c.getSimpleName().equals(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service class not found: " + className));

        // Find the method with @PreAuthorize
        Method targetMethod = findMethodByName(serviceClass, methodName);
        assertThat(targetMethod)
                .as("Method %s must exist on %s", methodName, className)
                .isNotNull();

        // Verify @PreAuthorize annotation exists
        PreAuthorize preAuthorize = targetMethod.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize)
                .as("Method %s.%s must have @PreAuthorize annotation", className, methodName)
                .isNotNull();

        // Verify the annotation contains the expected role
        String annotationValue = preAuthorize.value();
        assertThat(annotationValue)
                .as("@PreAuthorize on %s.%s must require role %s", className, methodName, expectedRole)
                .contains("hasRole('" + expectedRole + "')");

        // Verify that the given role either matches or doesn't match the required role
        boolean roleMatches = role.name().equals(expectedRole);
        boolean annotationAllowsRole = annotationValue.contains("hasRole('" + role.name() + "')");

        assertThat(annotationAllowsRole).isEqualTo(roleMatches);
    }

    @Property(tries = 20)
    void allProtectedMethods_havePreAuthorizeAnnotation(
            @ForAll("protectedMethodKeys") String methodKey
    ) {
        String[] parts = methodKey.split("\\.");
        String className = parts[0];
        String methodName = parts[1];

        Class<?> serviceClass = SERVICE_CLASSES.stream()
                .filter(c -> c.getSimpleName().equals(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service class not found: " + className));

        Method targetMethod = findMethodByName(serviceClass, methodName);
        assertThat(targetMethod).isNotNull();

        PreAuthorize preAuthorize = targetMethod.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize)
                .as("Protected method %s.%s must have @PreAuthorize", className, methodName)
                .isNotNull();
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.values());
    }

    @Provide
    Arbitrary<String> protectedMethodKeys() {
        return Arbitraries.of(new ArrayList<>(EXPECTED_ROLE_RESTRICTIONS.keySet()));
    }

    // --- Helpers ---

    private Method findMethodByName(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }
}
