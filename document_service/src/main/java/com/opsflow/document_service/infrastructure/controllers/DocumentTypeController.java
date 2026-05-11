package com.opsflow.document_service.infrastructure.controllers;

import com.opsflow.document_service.application.dtos.DocumentTypeDTO;
import com.opsflow.document_service.infrastructure.entities.DocumentType;
import com.opsflow.document_service.infrastructure.repositories.DocumentTypeJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint publico (autenticado) para obtener el catalogo de tipos de documento.
 *
 * <p>Lo consume el listado de documentos del frontend para reemplazar el id numerico de
 * "tipo" por el nombre legible. Es de solo lectura: cualquier mutacion del catalogo se hace
 * via SQL o migraciones para mantener la integridad referencial con {@code documents}.
 */
@RestController
@RequestMapping("/documents/types")
@RequiredArgsConstructor
@Tag(name = "Document Type Controller",
        description = "Catalogo de tipos de documento (solo lectura).")
public class DocumentTypeController {

    private final DocumentTypeJpaRepository repository;

    @Operation(summary = "Listar tipos de documento")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DocumentTypeDTO>> listAll() {
        List<DocumentTypeDTO> dtos = repository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private DocumentTypeDTO toDto(DocumentType t) {
        return new DocumentTypeDTO(t.getId(), t.getName(), t.getDescription());
    }
}
