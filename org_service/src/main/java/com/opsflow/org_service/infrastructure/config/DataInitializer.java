package com.opsflow.org_service.infrastructure.config;

import com.opsflow.org_service.domain.ports.in.OrganizationServicePort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private final OrganizationServicePort organizationService;

    public DataInitializer(OrganizationServicePort organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (organizationService.findAll().isEmpty()) {
            System.out.println(
                    "No se genero organizacion por defecto: el alta requiere manager inicial obligatorio.");
        }
    }
}
