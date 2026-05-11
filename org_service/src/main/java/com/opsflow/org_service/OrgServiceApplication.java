package com.opsflow.org_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.opsflow.org_service.infrastructure.security.OrgAuthorizationProperties;

@SpringBootApplication
@ComponentScan(basePackages = {"com.opsflow.org_service", "com.opsflow.common"})
@EnableFeignClients
@EnableConfigurationProperties(OrgAuthorizationProperties.class)
public class OrgServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrgServiceApplication.class, args);
    }

}
