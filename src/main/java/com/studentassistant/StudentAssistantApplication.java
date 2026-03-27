package com.studentassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudentAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentAssistantApplication.class, args);
    }
}
