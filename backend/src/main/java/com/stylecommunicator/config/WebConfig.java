package com.stylecommunicator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is now handled entirely inside Spring Security (SecurityConfig).
 * Having both a CorsFilter bean AND Spring Security CORS causes 403s on POST.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public WebConfig(AppProperties appProperties) {
        // appProperties kept in case other MVC config is added later
    }
}
