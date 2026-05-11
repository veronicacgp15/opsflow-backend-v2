package com.opsflow.document_service.infrastructure.repositories;

import com.opsflow.document_service.infrastructure.entities.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentTypeJpaRepository extends JpaRepository<DocumentType, Long> {
}
