package com.opsflow.document_service.infrastructure.controllers;

import com.opsflow.common.JwtUtils;
import com.opsflow.document_service.application.dtos.DocumentCreateDTO;
import com.opsflow.document_service.application.dtos.DocumentDownload;
import com.opsflow.document_service.application.dtos.DocumentUpdateDTO;
import com.opsflow.document_service.application.dtos.StoredFileInfo;
import com.opsflow.document_service.application.dtos.response.MessageResponse;
import com.opsflow.document_service.application.services.DocumentService;
import com.opsflow.document_service.domain.models.DocumentDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.opsflow.document_service.domain.constants.DocumentConstants.*;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Document Controller", description = "Endpoints for managing documents")
public class DocumentController {

    private final DocumentService documentService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "Create a new document", description = "Admin, Manager or User can create documents")
    @PostMapping(value = "/create", consumes = { "multipart/form-data" })
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<DocumentDomain> createDocument(
            @RequestPart("data") DocumentCreateDTO dto,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {

        Long jwtOrgId = jwtUtils.getOrganizationIdFromAuthentication(authentication);
        if (!jwtUtils.hasRole(authentication, "ADMIN") || dto.getOrganizationId() == null) {
            dto.setOrganizationId(jwtOrgId);
        }

        Long userId = jwtUtils.getUserIdFromAuthentication(authentication);
        dto.setUserId(userId);

        return new ResponseEntity<>(documentService.createDocument(dto, file), HttpStatus.CREATED);
    }

    @Operation(summary = "Get document by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentDomain> getDocumentById(@PathVariable Long id, Authentication authentication) {
        assertCanRead(id, authentication);
        return documentService.getDocumentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all documents")
    @GetMapping()
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DocumentDomain>> getAllDocuments(Authentication authentication) {
        if (jwtUtils.hasRole(authentication, "ADMIN")) {
            return ResponseEntity.ok(documentService.getAllDocuments());
        } else {
            Long orgId = jwtUtils.getOrganizationIdFromAuthentication(authentication);
            return ResponseEntity.ok(documentService.getDocumentsByOrganization(orgId));
        }
    }

    @Operation(summary = "Update document metadata")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<DocumentDomain> updateDocument(
            @PathVariable Long id,
            @RequestBody DocumentUpdateDTO dto,
            Authentication authentication) {
        assertCanModify(id, authentication, true);
        return ResponseEntity.ok(documentService.updateDocument(id, dto));
    }

    @Operation(summary = "Delete a document")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MessageResponse> deleteDocument(@PathVariable Long id, Authentication authentication) {
        assertCanModify(id, authentication, false);
        documentService.deleteDocument(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new MessageResponse(DOCUMENTO_CON_ID + id + ELIMINADO_EXITOSAMENTE));
    }

    @Operation(summary = "Upload a new version")
    @PostMapping("/add-version/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<DocumentDomain> uploadNewVersion(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        assertCanModify(id, authentication, true);
        Long userId = jwtUtils.getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(documentService.uploadNewVersion(id, file, userId));
    }

    @Operation(summary = "Delete a specific version")
    @DeleteMapping("/{id}/versions/{versionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<DocumentDomain> deleteVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            Authentication authentication) {
        assertCanModify(id, authentication, true);
        return ResponseEntity.ok(documentService.deleteVersion(id, versionId));
    }

    @Operation(summary = "Force state change", description = "Admin only")
    @PatchMapping("/{id}/force-state")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentDomain> forceStateChange(@PathVariable Long id, @RequestParam String state) {
        return ResponseEntity.ok(documentService.updateState(id, state));
    }

    @Operation(summary = "Descargar la ultima version del documento",
            description = "Devuelve el archivo binario con Content-Disposition inline para abrir en el navegador.")
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadLatest(@PathVariable Long id, Authentication authentication) {
        assertCanRead(id, authentication);
        return buildDownloadResponse(documentService.downloadVersion(id, null));
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            Authentication authentication) {
        assertCanRead(id, authentication);
        return buildDownloadResponse(documentService.downloadVersion(id, versionId));
    }

    @Operation(summary = "Listar archivos en el storage (R2 o disco local)")
    @GetMapping("/storage/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StoredFileInfo>> listStorage(
            @RequestParam(value = "prefix", required = false) String prefix) {
        return ResponseEntity.ok(documentService.listStoredFiles(prefix));
    }

    private ResponseEntity<byte[]> buildDownloadResponse(DocumentDownload download) {
        if (download.isExternal()) {
            String json = "{\"externalUrl\":\""
                    + download.externalUrl().replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\"}";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            headers.setContentLength(body.length);
            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        }


        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.data().length);

        return new ResponseEntity<>(download.data(), headers, HttpStatus.OK);
    }

    private void assertCanRead(Long documentId, Authentication authentication) {
        if (jwtUtils.hasRole(authentication, "ADMIN")) return;

        Long orgId = jwtUtils.getOrganizationIdFromAuthentication(authentication);
        boolean ok = documentService.getDocumentById(documentId)
                .map(doc -> orgId != null && orgId.equals(doc.getOrganizationId()))
                .orElse(false);

        if (!ok) {
            throw new AccessDeniedException(
                    DOCUMENTO_ORGANIZACION_NO_EXISTE);
        }
    }

    private void assertCanModify(Long documentId, Authentication authentication, boolean allowOwner) {
        if (jwtUtils.hasRole(authentication, "ADMIN")) return;

        var docOpt = documentService.getDocumentById(documentId);
        if (docOpt.isEmpty()) {
            throw new AccessDeniedException("Documento " + documentId + " no encontrado.");
        }
        DocumentDomain doc = docOpt.get();

        Long orgId = jwtUtils.getOrganizationIdFromAuthentication(authentication);
        Long userId = jwtUtils.getUserIdFromAuthentication(authentication);

        boolean managerInOrg = jwtUtils.hasRole(authentication, "MANAGER")
                && orgId != null && orgId.equals(doc.getOrganizationId());
        boolean ownerOk = allowOwner
                && userId != null && userId.equals(doc.getOwnerId());

        if (!managerInOrg && !ownerOk) {
            throw new AccessDeniedException(
                    SIN_ACCESO_A_DOCUMENTO);
        }
    }
}
