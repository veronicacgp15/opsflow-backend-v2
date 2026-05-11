package com.opsflow.document_service.application.services;

import com.opsflow.document_service.application.dtos.DocumentCreateDTO;
import com.opsflow.document_service.application.dtos.DocumentDownload;
import com.opsflow.document_service.application.dtos.DocumentUpdateDTO;
import com.opsflow.document_service.application.dtos.StoredFileInfo;
import com.opsflow.document_service.domain.models.DocumentDomain;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface DocumentService {
    DocumentDomain createDocument(DocumentCreateDTO dto, MultipartFile file);
    Optional<DocumentDomain> getDocumentById(Long id);
    List<DocumentDomain> getAllDocuments();
    void deleteDocument(Long id);
    DocumentDomain uploadNewVersion(Long documentId, MultipartFile file, Long userId);
    DocumentDomain deleteVersion(Long documentId, Long versionId);
    DocumentDomain updateDocument(Long id, DocumentUpdateDTO dto);
    List<DocumentDomain> getDocumentsByOrganization(Long organizationId);
    DocumentDomain updateState(Long id, String state);
    DocumentDownload downloadVersion(Long documentId, Long versionId);
    List<StoredFileInfo> listStoredFiles(String prefix);
}
