package com.opsflow.document_service.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
public class R2ClientConfig {

    @Bean
    public S3Client r2S3Client(R2StorageProperties props) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(
                props.getAccessKeyId(),
                props.getSecretAccessKey()
        );

        ApacheHttpClient.Builder httpClient = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(15))
                .useIdleConnectionReaper(true);

        ClientOverrideConfiguration overrides = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(30))
                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                .retryStrategy(RetryMode.STANDARD)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion() == null ? "auto" : props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .httpClientBuilder(httpClient)
                .overrideConfiguration(overrides)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(true)
                        .build())
                .build();
    }
}
