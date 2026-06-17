package com.stylecommunicator.filter;

import com.stylecommunicator.config.AppProperties;
import com.stylecommunicator.security.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final AppProperties appProperties;
    private final JwtService jwtService;
    private final Map<String, Integer> dailySessionCount = new ConcurrentHashMap<>();
    private volatile LocalDate currentDay = LocalDate.now();

    public RateLimitFilter(AppProperties appProperties, JwtService jwtService) {
        this.appProperties = appProperties;
        this.jwtService = jwtService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        resetIfNewDay();

        String path = httpRequest.getRequestURI();
        if (path != null && path.endsWith("/api/sessions/start")) {
            // Extract userId from JWT Bearer token
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtService.isValid(token)) {
                        UUID userId = jwtService.extractUserId(token);
                        String key = currentDay + ":" + userId;
                        int count = dailySessionCount.getOrDefault(key, 0);
                        if (count >= appProperties.getDailySessionCap()) {
                            httpResponse.sendError(429, "Daily limit reached. Try tomorrow.");
                            return;
                        }
                        dailySessionCount.put(key, count + 1);
                    }
                } catch (Exception ignored) {
                    // Invalid token — let Spring Security handle it
                }
            }
        }

        chain.doFilter(request, response);
    }

    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            synchronized (this) {
                if (!today.equals(currentDay)) {
                    dailySessionCount.clear();
                    currentDay = today;
                }
            }
        }
    }
}
