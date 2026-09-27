package com.stylecommunicator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes /v3/api-docs and /swagger-ui.html. Access to those paths is
 * governed by SecurityConfig, not here — locked to ADMIN in prod, open in
 * dev, since the docs describe the full API surface.
 */
@Configuration
public class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI styleCommunicatorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Style Communicator API")
                        .description("""
                                Practice written communication in a chosen style: pick or describe a
                                persona, get put into a generated situation, submit a response, and
                                get AI-scored feedback across six dimensions.

                                Auth: login/register set an HttpOnly cookie (`sc_token`) — there's
                                no bearer token to paste in here. Use "Try it out" after calling
                                /api/auth/login in the same browser session and the cookie is sent
                                automatically.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("sc_token")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
    }
}
