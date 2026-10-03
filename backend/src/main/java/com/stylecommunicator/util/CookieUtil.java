package com.stylecommunicator.util;

import com.stylecommunicator.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public static final String COOKIE_NAME = "sc_token";
    // 24 hours
    private static final int MAX_AGE = 86400;

    private final AppProperties appProperties;

    public CookieUtil(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** Write the JWT into an HttpOnly cookie on the response. */
    public void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);                         // JS cannot read this
        cookie.setSecure(appProperties.isCookieSecure()); // HTTPS only on prod
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE);
        cookie.setAttribute("SameSite", appProperties.isCookieSecure() ? "None" : "Lax");
        response.addCookie(cookie);
    }

    /** Overwrite the cookie with an empty value and maxAge=0 to delete it. */
    public void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(appProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", appProperties.isCookieSecure() ? "None" : "Lax");
        response.addCookie(cookie);
    }
}
