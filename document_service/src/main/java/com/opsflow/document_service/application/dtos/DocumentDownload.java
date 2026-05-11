package com.opsflow.document_service.application.dtos;

public record DocumentDownload(
        byte[] data,
        String filename,
        String contentType,
        String externalUrl
) {
    public boolean isExternal() {
        return externalUrl != null && !externalUrl.isBlank();
    }
}
