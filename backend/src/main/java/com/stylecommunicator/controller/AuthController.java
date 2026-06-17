package com.stylecommunicator.controller;

import com.stylecommunicator.entity.AppUser;
import com.stylecommunicator.repository.AppUserRepository;
import com.stylecommunicator.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String token,
            UUID userId,
            String email,
            String role
    ) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        AppUser user = new AppUser();
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole());
    }

    @GetMapping("/me")
    public AuthResponse me(jakarta.servlet.http.HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("authenticatedUserId");
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return new AuthResponse(null, user.getId(), user.getEmail(), user.getRole());
    }
}
