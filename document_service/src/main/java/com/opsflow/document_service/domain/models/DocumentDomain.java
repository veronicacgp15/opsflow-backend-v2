package com.opsflow.document_service.domain.models;


import com.opsflow.document_service.domain.enums.DocumentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DocumentDomain {
    private Long id;
    private String name;
    private Long documentTypeId;
    private DocumentStatus status;
    private LocalDate expirationDate;
    private Long userId;
    private Long organizationId;
    private String targetEntityType;
    private Long targetEntityId;
    private List<DocumentVersionDomain> versions = new ArrayList<>();

    public Long getOwnerId() {
        return userId;
    }

    public void setOwnerId(Long ownerId) {
        this.userId = ownerId;
    }

    public void addNewVersion(String fileUrl, Long fileSize, Long uploaderId) {
        if (this.versions == null) {
            this.versions = new ArrayList<>();
        }
        DocumentVersionDomain newVersion = new DocumentVersionDomain();
        newVersion.setVersionNumber(this.versions.size() + 1);
        newVersion.setFileUrl(fileUrl);
        newVersion.setFileSize(fileSize);
        newVersion.setUploadedByUserId(uploaderId);
        newVersion.setCreatedAt(LocalDateTime.now());

        this.versions.add(newVersion);
    }

    public void updateStatusBasedOnDate(LocalDate today) {
        if (expirationDate == null) return;

        if (today.isAfter(expirationDate)) {
            this.status = DocumentStatus.EXPIRED;
        } else if (today.plusDays(30).isAfter(expirationDate) || today.plusDays(30).isEqual(expirationDate)) {
            this.status = DocumentStatus.EXPIRING;
        } else {
            this.status = DocumentStatus.ACTIVE;
        }
    }

}
