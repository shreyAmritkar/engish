package com.stylecommunicator.config;

import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.Map;

@Configuration
public class MetricsConfig {

    @Value("${OTLP_METRICS_ENDPOINT}")
    private String otlpUrl;

    @Value("${OTLP_INSTANCE_ID}")
    private String instanceId;

    @Value("${OTLP_API_TOKEN}")
    private String apiToken;

    @Bean
    public OtlpMeterRegistry otlpMeterRegistry() {
        String encoded = Base64.getEncoder()
            .encodeToString((instanceId + ":" + apiToken).getBytes());

        OtlpConfig config = new OtlpConfig() {
            @Override
            public String url() { return otlpUrl; }

            @Override
            public Map<String, String> headers() {
                return Map.of("Authorization", "Basic " + encoded);
            }

            @Override
            public String get(String key) { return null; }
        };

        return new OtlpMeterRegistry(config, Clock.SYSTEM);
    }
}