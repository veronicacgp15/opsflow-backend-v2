package com.opsflow.document_service.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mapea el bloque {@code storage.r2} de application.yml.
 * <p>
 * Cloudflare R2 expone una API S3-compatible. Tomamos los valores del panel:
 * <ul>
 *   <li>endpoint: "API S3" en Detalles de la cuenta.</li>
 *   <li>bucket: nombre que diste al cubo.</li>
 *   <li>access-key-id / secret-access-key: Administrar -> API Tokens.</li>
 *   <li>public-base-url: dominio publico opcional (r2.dev o un dominio personalizado).</li>
 * </ul>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "storage.r2")
public class R2StorageProperties {

    private boolean enabled = false;

    private String endpoint;

    private String region = "auto";

    private String bucket;

    private String accessKeyId;

    private String secretAccessKey;

    private String publicBaseUrl;
}
