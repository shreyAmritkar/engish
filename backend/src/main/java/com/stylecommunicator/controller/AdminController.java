package com.stylecommunicator.controller;

import com.stylecommunicator.entity.AppUser;
import com.stylecommunicator.repository.AppUserRepository;
import com.stylecommunicator.service.RivalLeaderboardJob;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppUserRepository userRepository;
    private final RivalLeaderboardJob rivalLeaderboardJob;

    public AdminController(AppUserRepository userRepository, RivalLeaderboardJob rivalLeaderboardJob) {
        this.userRepository = userRepository;
        this.rivalLeaderboardJob = rivalLeaderboardJob;
    }

    public record UserSummary(UUID id, String email, String role, Instant createdAt) {
        static UserSummary from(AppUser u) {
            return new UserSummary(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt());
        }
    }

    public record ChangeRoleRequest(String role) {}

    /** List all users */
    @GetMapping("/users")
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream().map(UserSummary::from).toList();
    }

    /** Promote or demote a user's role */
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

    /** Delete a user */
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(userId);
    }

    /**
     * TEMPORARY — manual trigger for the rival leaderboard cron job, for testing.
     * Delete this endpoint once the feature is verified working in production;
     * the cron schedule (3 AM daily) is the real trigger going forward.
     */
    @PostMapping("/leaderboard/rebuild")
    public Map<String, String> rebuildLeaderboard() {
        rivalLeaderboardJob.rebuild();
        return Map.of("status", "rebuild triggered — per-dimension leaderboards rebuilt inline, " +
                "per-user rival lists rebuilding asynchronously");
    }
}                 
