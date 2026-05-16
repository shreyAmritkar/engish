package com.stylecommunicator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String apiKey = "";
    private String flashModel = "gemini-1.5-flash";
    private String proModel = "gemini-1.5-pro";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getFlashModel() { return flashModel; }
    public void setFlashModel(String flashModel) { this.flashModel = flashModel; }
    public String getProModel() { return proModel; }
    public void setProModel(String proModel) { this.proModel = proModel; }
}
