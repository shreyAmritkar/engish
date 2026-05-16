package com.stylecommunicator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StyleCommunicatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StyleCommunicatorApplication.class, args);
    }
}
