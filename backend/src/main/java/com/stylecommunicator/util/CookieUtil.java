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
        // SameSite=None: required because frontend (Vercel) and backend (Render)
        // are different domains, so every API call is a cross-site request.
        // SameSite=Lax silently drops the cookie on cross-site fetch/XHR POSTs
        // (it only allows top-level navigation GETs), which is why auth worked
        // on permitAll() GET endpoints but failed with 403 on POST endpoints
        // requiring authentication. SameSite=None REQUIRES Secure=true, which
        // is already true in production (Render serves over HTTPS).
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
