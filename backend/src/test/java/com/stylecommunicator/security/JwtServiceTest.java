package com.stylecommunicator.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 32+ chars required for HS256
    private static final String SECRET = "test-secret-key-must-be-32-chars-long!!";
    private static final long EXPIRY_MS = 86_400_000L; // 24h

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRY_MS);
    }

    // ── generateToken ─────────────────────────────────────────────────────

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@test.com", "USER");
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_producesThreePartJwt() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@test.com", "USER");
        assertEquals(3, token.split("\\.").length, "JWT must have 3 dot-separated parts");
    }

    // ── parseToken / extractUserId ────────────────────────────────────────

    @Test
    void extractUserId_returnsOriginalUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@test.com", "USER");
        assertEquals(userId, jwtService.extractUserId(token));
    }

    @Test
    void extractRole_returnsOriginalRole() {
        String token = jwtService.generateToken(UUID.randomUUID(), "admin@test.com", "ADMIN");
        assertEquals("ADMIN", jwtService.extractRole(token));
    }

    @Test
    void parseToken_containsEmailClaim() {
        String token = jwtService.generateToken(UUID.randomUUID(), "hello@test.com", "USER");
        Claims claims = jwtService.parseToken(token);
        assertEquals("hello@test.com", claims.get("email", String.class));
    }

    // ── isValid ───────────────────────────────────────────────────────────

    @Test
    void isValid_validToken_returnsTrue() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@test.com", "USER");
        assertTrue(jwtService.isValid(token));
    }

    @Test
    void isValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@test.com", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtService.isValid(tampered));
    }

    @Test
    void isValid_blankToken_returnsFalse() {
        assertFalse(jwtService.isValid(""));
        assertFalse(jwtService.isValid("   "));
    }

    @Test
    void isValid_randomString_returnsFalse() {
        assertFalse(jwtService.isValid("not.a.jwt"));
    }

    // ── Expired token ─────────────────────────────────────────────────────

    @Test
    void isValid_expiredToken_returnsFalse() {
        JwtService shortLived = new JwtService(SECRET, 1L); // 1ms expiry
        String token = shortLived.generateToken(UUID.randomUUID(), "user@test.com", "USER");

        // Token expires almost instantly
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertFalse(shortLived.isValid(token), "Expired token should be invalid");
    }

    @Test
    void parseToken_expiredToken_throwsExpiredJwtException() {
        JwtService shortLived = new JwtService(SECRET, 1L);
        String token = shortLived.generateToken(UUID.randomUUID(), "user@test.com", "USER");
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThrows(ExpiredJwtException.class, () -> shortLived.parseToken(token));
    }

    // ── Wrong secret ──────────────────────────────────────────────────────

    @Test
    void isValid_tokenSignedWithDifferentSecret_returnsFalse() {
        JwtService other = new JwtService("completely-different-secret-key-xyz!!", EXPIRY_MS);
        String token = other.generateToken(UUID.randomUUID(), "user@test.com", "USER");
        assertFalse(jwtService.isValid(token),
            "Token signed with different secret should be invalid");
    }

    // ── Different roles ───────────────────────────────────────────────────

    @Test
    void extractRole_userRole() {
        String token = jwtService.generateToken(UUID.randomUUID(), "u@test.com", "USER");
        assertEquals("USER", jwtService.extractRole(token));
    }

    @Test
    void extractRole_adminRole() {
        String token = jwtService.generateToken(UUID.randomUUID(), "a@test.com", "ADMIN");
        assertEquals("ADMIN", jwtService.extractRole(token));
    }
}
