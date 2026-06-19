package com.stylecommunicator.security;

import com.stylecommunicator.util.CookieUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null || !jwtService.isValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = jwtService.parseToken(token);
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);

        request.setAttribute("authenticatedUserId", userId);

        var auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    /**
     * Token extraction priority:
     * 1. HttpOnly cookie  — preferred, JS-inaccessible
     * 2. Authorization header — fallback for API / curl clients
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Cookie
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (CookieUtil.COOKIE_NAME.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    return (value != null && !value.isBlank()) ? value : null;
                }
            }
        }

        // 2. Authorization: Bearer header (API fallback)
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }
}
