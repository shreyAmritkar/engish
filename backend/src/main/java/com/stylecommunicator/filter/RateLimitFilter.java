package com.stylecommunicator.filter;

import com.stylecommunicator.config.AppProperties;
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
    private final Map<String, Integer> dailySessionCount = new ConcurrentHashMap<>();
    private volatile LocalDate currentDay = LocalDate.now();

    public RateLimitFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        resetIfNewDay();

        String path = httpRequest.getRequestURI();
        if (path != null && path.endsWith("/api/sessions/start")) {
            String userIdHeader = httpRequest.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    UUID.fromString(userIdHeader);
                    String key = currentDay + ":" + userIdHeader;
                    int count = dailySessionCount.getOrDefault(key, 0);
                    if (count >= appProperties.getDailySessionCap()) {
                        httpResponse.sendError(429, "Daily limit reached. Try tomorrow.");
                        return;
                    }
                    dailySessionCount.put(key, count + 1);
                } catch (IllegalArgumentException ignored) {
                    // invalid UUID — let controller validate
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
