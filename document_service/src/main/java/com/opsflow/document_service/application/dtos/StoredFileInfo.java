package com.opsflow.document_service.application.dtos;

import java.time.Instant;


public record StoredFileInfo(
        String key,
        Long size,
        Instant lastModified,
        String publicUrl
) {}
