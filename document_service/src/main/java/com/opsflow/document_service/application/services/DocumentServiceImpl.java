package com.opsflow.document_service.application.services;

import com.opsflow.document_service.application.dtos.DocumentCreateDTO;
import com.opsflow.document_service.application.dtos.DocumentDownload;
import com.opsflow.document_service.application.dtos.DocumentUpdateDTO;
import com.opsflow.document_service.application.dtos.StoredFileInfo;
import com.opsflow.document_service.domain.enums.DocumentStatus;
import com.opsflow.document_service.domain.models.DocumentDomain;
import com.opsflow.document_service.domain.models.DocumentVersionDomain;
import com.opsflow.document_service.domain.port.out.DocumentEventPublisherPort;
import com.opsflow.document_service.domain.port.out.DocumentRepositoryPort;
import com.opsflow.document_service.domain.port.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    public static final int MAX_VERSIONS_PER_DOCUMENT = 3;

    private final DocumentRepositoryPort documentRepositoryPort;
    private final DocumentEventPublisherPort eventPublisherPort;
    private final FileStoragePort fileStoragePort;

    @Override
    @Transactional
    public DocumentDomain createDocument(DocumentCreateDTO dto, MultipartFile file) {
        DocumentDomain document = new DocumentDomain();
        document.setName(dto.getName());
        document.setDocumentTypeId(dto.getDocumentTypeId());
        document.setExpirationDate(dto.getExpirationDate());
        document.setOwnerId(dto.getUserId());
        document.setOrganizationId(dto.getOrganizationId());
        document.setTargetEntityType(dto.getTargetEntityType());
        document.setTargetEntityId(dto.getTargetEntityId());
        document.setStatus(DocumentStatus.ACTIVE);
        
        document = documentRepositoryPort.save(document);

        String fileUrl = fileStoragePort.uploadFile(file, generateFolderPath(document, 1));
        
        ensureVersionsList(document);
        document.addNewVersion(fileUrl, file.getSize(), dto.getUserId());

        return saveAndPublish(document, true);
    }

    @Override
    public Optional<DocumentDomain> getDocumentById(Long id) {
        return documentRepositoryPort.findById(id);
    }

    @Override
    public List<DocumentDomain> getAllDocuments() {
        return documentRepositoryPort.findAll();
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        documentRepositoryPort.deleteById(id);
    }

    @Override
    @Transactional
    public DocumentDomain uploadNewVersion(Long documentId, MultipartFile file, Long userId) {
        DocumentDomain document = findOrThrow(documentId);
        int currentCount = document.getVersions() != null ? document.getVersions().size() : 0;
        if (currentCount >= MAX_VERSIONS_PER_DOCUMENT) {
            throw new IllegalStateException(
                    "Este documento ya tiene " + MAX_VERSIONS_PER_DOCUMENT + " versiones, " +
                            "que es el maximo permitido. Elimina una version para poder subir otra.");
        }

        int nextVersion = getNextVersionNumber(document);
        String fileUrl = fileStoragePort.uploadFile(file, generateFolderPath(document, nextVersion));
        
        document.addNewVersion(fileUrl, file.getSize(), userId);

        return saveAndPublish(document, false);
    }

    @Override
    @Transactional
    public DocumentDomain deleteVersion(Long documentId, Long versionId) {
        DocumentDomain document = findOrThrow(documentId);
        ensureVersionsList(document);

        DocumentVersionDomain target = document.getVersions().stream()
                .filter(v -> versionId.equals(v.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Version " + versionId + " not found in document " + documentId));

        if (target.getFileUrl() != null && !target.getFileUrl().isBlank() && !isAbsoluteUrl(target.getFileUrl())) {
            fileStoragePort.deleteFile(target.getFileUrl());
        }

        document.getVersions().removeIf(v -> versionId.equals(v.getId()));
        return saveAndPublish(document, false);
    }

    @Override
    @Transactional
    public DocumentDomain updateDocument(Long id, DocumentUpdateDTO dto) {
        DocumentDomain document = findOrThrow(id);

        Optional.ofNullable(dto.getName()).ifPresent(document::setName);
        Optional.ofNullable(dto.getDocumentTypeId()).ifPresent(document::setDocumentTypeId);
        Optional.ofNullable(dto.getExpirationDate()).ifPresent(document::setExpirationDate);
        Optional.ofNullable(dto.getTargetEntityType()).ifPresent(document::setTargetEntityType);
        Optional.ofNullable(dto.getTargetEntityId()).ifPresent(document::setTargetEntityId);

        return saveAndPublish(document, false);
    }

    @Override
    public List<DocumentDomain> getDocumentsByOrganization(Long organizationId) {
        return documentRepositoryPort.findAll().stream()
                .filter(doc -> organizationId.equals(doc.getOrganizationId()))
                .toList();
    }

    @Override
    @Transactional
    public DocumentDomain updateState(Long id, String state) {
        DocumentDomain document = findOrThrow(id);
        document.setStatus(DocumentStatus.valueOf(state.toUpperCase()));
        return saveAndPublish(document, false);
    }

    @Override
    public DocumentDownload downloadVersion(Long documentId, Long versionId) {
        DocumentDomain document = findOrThrow(documentId);
        List<DocumentVersionDomain> versions = document.getVersions();
        if (versions == null || versions.isEmpty()) {
            throw new RuntimeException("Document has no versions: id=" + documentId);
        }

        DocumentVersionDomain version;
        if (versionId == null) {
            version = versions.stream()
                    .max(Comparator.comparingInt(DocumentVersionDomain::getVersionNumber))
                    .orElseThrow(() -> new RuntimeException("Document has no versions: id=" + documentId));
        } else {
            version = versions.stream()
                    .filter(v -> versionId.equals(v.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "Version " + versionId + " not found in document " + documentId));
        }

        String fileUrl = version.getFileUrl();
        String originalName = extractOriginalFilename(fileUrl);

        String filename = buildDownloadFilename(document, version, originalName);
        String contentType = guessContentType(originalName);

        // Documentos seed (data.sql) o legacy pueden tener una URL absoluta (S3, https://...).
        // En ese caso NO intentamos leerlos del disco local: devolvemos el DownloadResult con
        // la URL para que el controller responda con un redirect 302. Asi el cliente sigue al
        // storage externo si existe; si la URL es ficticia (semilla local), el browser
        // mostrara su propio error de red, pero el backend NO se rompe.
        if (isAbsoluteUrl(fileUrl)) {
            return new DocumentDownload(null, filename, contentType, fileUrl);
        }

        byte[] data = fileStoragePort.downloadFile(fileUrl);
        return new DocumentDownload(data, filename, contentType, null);
    }

    private String buildDownloadFilename(DocumentDomain doc,
                                         DocumentVersionDomain version,
                                         String originalName) {
        String base = doc.getName() != null && !doc.getName().isBlank()
                ? doc.getName().trim()
                : "documento";

        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
        String ext = extractExtension(originalName);
        int n = version.getVersionNumber() != null ? version.getVersionNumber() : 1;
        return ext.isEmpty()
                ? String.format("%s_v%d", base, n)
                : String.format("%s_v%d.%s", base, n, ext);
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1);
    }

    @Override
    public List<StoredFileInfo> listStoredFiles(String prefix) {
        return fileStoragePort.listFiles(prefix);
    }

    private boolean isAbsoluteUrl(String fileUrl) {
        if (fileUrl == null) return false;
        String lower = fileUrl.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String extractOriginalFilename(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return "documento";
        String last = fileUrl;
        int slash = last.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < last.length()) {
            last = last.substring(slash + 1);
        }
        int underscore = last.indexOf('_');
        if (underscore >= 0 && underscore + 1 < last.length()) {
            return last.substring(underscore + 1);
        }
        return last;
    }

    private String guessContentType(String filename) {
        String guess = URLConnection.guessContentTypeFromName(filename);
        return guess != null ? guess : "application/octet-stream";
    }

    private DocumentDomain findOrThrow(Long id) {
        return documentRepositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with ID: " + id));
    }

    private DocumentDomain saveAndPublish(DocumentDomain document, boolean isNew) {
        DocumentDomain saved = documentRepositoryPort.save(document);
        if (isNew) eventPublisherPort.publishDocumentCreatedEvent(saved);
        else eventPublisherPort.publishDocumentUpdatedEvent(saved);
        return saved;
    }

    private void ensureVersionsList(DocumentDomain document) {
        if (document.getVersions() == null) document.setVersions(new ArrayList<>());
    }

    private int getNextVersionNumber(DocumentDomain document) {
        if (document.getVersions() == null || document.getVersions().isEmpty()) {
            return 1;
        }
        return document.getVersions().stream()
                .map(DocumentVersionDomain::getVersionNumber)
                .filter(v -> v != null && v > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String generateFolderPath(DocumentDomain doc, int version) {
        return String.format("documents/org_%d/doc_%d/v%d", doc.getOrganizationId(), doc.getId(), version);
    }
}
