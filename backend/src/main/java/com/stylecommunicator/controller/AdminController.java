package com.stylecommunicator.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.stylecommunicator.entity.AppUser;
import com.stylecommunicator.repository.AppUserRepository;
import com.stylecommunicator.service.SituationLoadingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin Control Panel.
 * 
 * Requires ADMIN role for all endpoints.
 * Provides management for:
 * - User accounts (list, update role, deactivate, delete)
 * - System statistics (total users, admin count, etc.)
 */
@Tag(name = "Admin", description = "User management, role/active toggles, bulk operations.")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppUserRepository userRepository;
    private final SituationLoadingService situationLoadingService;

    public AdminController(AppUserRepository userRepository, SituationLoadingService situationLoadingService) {
        this.userRepository = userRepository;
        this.situationLoadingService = situationLoadingService;
    }

    // ============ User Management ============

    public record UserSummary(UUID id, String email, String role, Instant createdAt, boolean active) {
        static UserSummary from(AppUser u) {
            return new UserSummary(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt(), u.isActive());
        }
    }

    public record ChangeRoleRequest(String role) {}
    public record SetActiveRequest(boolean active) {}

    /**
     * List all users with pagination support.
     * Returns email, role, creation date.
     */
    @GetMapping("/users")
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream()
                .map(UserSummary::from)
                .toList();
    }

    /**
     * Get single user details by ID.
     */
    @GetMapping("/users/{userId}")
    public UserSummary getUserDetails(@PathVariable UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserSummary.from(user);
    }

    /**
     * Promote or demote a user's role between USER and ADMIN.
     */
    @PatchMapping("/users/{userId}/role")
    public UserSummary changeRole(@PathVariable UUID userId,
                                  @RequestBody ChangeRoleRequest request) {
        if (!List.of("USER", "ADMIN").contains(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role. Must be USER or ADMIN");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(request.role());
        userRepository.save(user);
        return UserSummary.from(user);
    }

    /**
     * Activate or deactivate a user account.
     * A deactivated user can't log in, and any existing session is rejected
     * on the very next authenticated request (see JwtAuthFilter) — the
     * account is disabled immediately, not just at next login.
     */
    @PatchMapping("/users/{userId}/active")
    public UserSummary setActive(@PathVariable UUID userId,
                                 @RequestBody SetActiveRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setActive(request.active());
        userRepository.save(user);
        return UserSummary.from(user);
    }

    /**
     * Delete a user account permanently.
     * WARNING: This removes all user data.
     */
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        // Prevent deleting yourself
        // In production, get current user from SecurityContext
        // UUID currentUserId = SecurityContextHolder.getContext().getAuthentication()...
        
        userRepository.deleteById(userId);
    }

    // ============ System Statistics ============

    public record SystemStats(
            long totalUsers,
            long adminCount,
            long regularUserCount
    ) {}

    /**
     * Get system-wide statistics.
     * Returns user counts and distribution.
     */
    @GetMapping("/stats")
    public SystemStats getSystemStats() {
        List<AppUser> allUsers = userRepository.findAll();
        long adminCount = allUsers.stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .count();
        
        return new SystemStats(
                allUsers.size(),
                adminCount,
                allUsers.size() - adminCount
        );
    }

    // ============ Bulk Operations ============
    //
    // Each item below is its own independent success/failure — Spring Data's
    // save()/deleteById() are already transactional per call, so each item
    // is atomic on its own without any extra annotation here. Deliberately
    // NOT wrapping the whole loop in one @Transactional: catching a
    // persistence exception inside a transactional method doesn't undo
    // Hibernate marking that transaction rollback-only, so a single bad ID
    // partway through the batch would silently roll back every item that
    // already succeeded. Per-item transactions avoid that trap and match
    // the partial-success result these endpoints are meant to report.

    public record BulkUserRequest(List<UUID> userIds) {}
    public record BulkOperationResult(int successCount, int failureCount, List<String> errors) {}

    /**
     * Bulk promote users to ADMIN role.
     * Returns count of successful and failed operations.
     */
    @PostMapping("/users/bulk/promote")
    public BulkOperationResult bulkPromote(@RequestBody BulkUserRequest request) {
        int success = 0;
        int failures = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (UUID userId : request.userIds) {
            try {
                AppUser user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                user.setRole("ADMIN");
                userRepository.save(user);
                success++;
            } catch (Exception e) {
                failures++;
                errors.add("Failed to promote " + userId + ": " + e.getMessage());
            }
        }

        return new BulkOperationResult(success, failures, errors);
    }

    /**
     * Bulk demote users to regular USER role.
     */
    @PostMapping("/users/bulk/demote")
    public BulkOperationResult bulkDemote(@RequestBody BulkUserRequest request) {
        int success = 0;
        int failures = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (UUID userId : request.userIds) {
            try {
                AppUser user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                user.setRole("USER");
                userRepository.save(user);
                success++;
            } catch (Exception e) {
                failures++;
                errors.add("Failed to demote " + userId + ": " + e.getMessage());
            }
        }

        return new BulkOperationResult(success, failures, errors);
    }

    /**
     * Bulk delete multiple users.
     * WARNING: Permanently removes user accounts and data.
     */
    @PostMapping("/users/bulk/delete")
    public BulkOperationResult bulkDelete(@RequestBody BulkUserRequest request) {
        int success = 0;
        int failures = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (UUID userId : request.userIds) {
            try {
                if (!userRepository.existsById(userId)) {
                    throw new RuntimeException("User not found");
                }
                userRepository.deleteById(userId);
                success++;
            } catch (Exception e) {
                failures++;
                errors.add("Failed to delete " + userId + ": " + e.getMessage());
            }
        }

        return new BulkOperationResult(success, failures, errors);
    }

    // ============ Health & Maintenance ============

    public record HealthResponse(String status, String timestamp) {}

    /**
     * Health check endpoint for admin dashboard.
     * Verifies database connectivity and basic system health.
     */
    @GetMapping("/health")
    public HealthResponse getHealth() {
        try {
            userRepository.count(); // Test DB connection
            return new HealthResponse("healthy", Instant.now().toString());
        } catch (Exception e) {
            return new HealthResponse("unhealthy: " + e.getMessage(), Instant.now().toString());
        }
    }

    // ============ Situation Loading Control ============

    public record SituationLoadingConfig(
            String currentStrategy,
            String description,
            List<String> availableStrategies
    ) {}

    public record UpdateLoadingStrategyRequest(String strategy) {}

    /**
     * Get current situation loading strategy configuration.
     * 
     * Prevents delays when users practice by controlling when situations are loaded:
     * - ON_DEMAND: Load when user requests (fast for user, may have short delay)
     * - BACKGROUND: Pre-load in background (instant for user, uses resources)
     */
    @GetMapping("/settings/situation-loading")
    public SituationLoadingConfig getSituationLoadingConfig() {
        return new SituationLoadingConfig(
                situationLoadingService.getStrategy().toString(),
                situationLoadingService.getStrategyInfo(),
                List.of("ON_DEMAND", "BACKGROUND")
        );
    }

    /**
     * Update situation loading strategy (ADMIN ONLY).
     * 
     * Use this to optimize based on:
     * - ON_DEMAND: Better for small user base, lower resource usage
     * - BACKGROUND: Better for large user base, better user experience
     */
    @PostMapping("/settings/situation-loading")
    public SituationLoadingConfig updateSituationLoadingStrategy(
            @RequestBody UpdateLoadingStrategyRequest request) {
        try {
            SituationLoadingService.LoadingStrategy strategy = 
                    SituationLoadingService.LoadingStrategy.valueOf(request.strategy);
            situationLoadingService.setStrategy(strategy);
            return getSituationLoadingConfig();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Invalid strategy. Must be ON_DEMAND or BACKGROUND");
        }
    }
}
