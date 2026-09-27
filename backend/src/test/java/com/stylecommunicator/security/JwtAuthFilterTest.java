package com.stylecommunicator.security;

import com.stylecommunicator.entity.AppUser;
import com.stylecommunicator.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the account-deactivation check added to JwtAuthFilter: a valid,
 * unexpired JWT must still be rejected the moment the underlying account
 * is deactivated or deleted, not just at next login.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-must-be-32-chars-long!!";
    private static final long EXPIRY_MS = 86_400_000L;

    @Mock private AppUserRepository userRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private JwtAuthFilter filter;
    private JwtService jwtService;
    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRY_MS);
        filter = new JwtAuthFilter(jwtService, userRepository);

        userId = UUID.randomUUID();
        token = jwtService.generateToken(userId, "user@test.com", "USER");

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        lenient().when(request.getCookies()).thenReturn(null);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUser_isAuthenticatedAndChainProceeds() throws Exception {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setActive(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void deactivatedUser_isRejectedWithForbidden() throws Exception {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void deletedUser_isRejectedWithForbidden() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
    }
}
