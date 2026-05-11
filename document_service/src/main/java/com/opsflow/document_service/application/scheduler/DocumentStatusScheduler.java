package com.opsflow.document_service.application.scheduler;

import com.opsflow.document_service.domain.enums.DocumentStatus;
import com.opsflow.document_service.domain.models.DocumentDomain;
import com.opsflow.document_service.domain.port.out.DocumentEventPublisherPort;
import com.opsflow.document_service.domain.port.out.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentStatusScheduler {

    private final DocumentRepositoryPort repositoryPort;
    private final DocumentEventPublisherPort eventPublisher;

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void checkDocumentStatuses() {
            log.info("Iniciando escaneo diario de estados de documentos...");
            LocalDate today = LocalDate.now();

            repositoryPort.findAll().forEach(doc -> {
                DocumentStatus previousStatus = doc.getStatus();
                doc.updateStatusBasedOnDate(today);

                if (previousStatus != doc.getStatus()) {
                    repositoryPort.save(doc);
                    publishEventIfApplicable(doc);
                }
            });

            log.info("Escaneo diario de documentos finalizado exitosamente.");
        }
        private void publishEventIfApplicable(DocumentDomain doc) {
            switch (doc.getStatus()) {
                case EXPIRING -> eventPublisher.publishDocumentExpiringEvent(doc);
                case EXPIRED -> eventPublisher.publishDocumentExpiredEvent(doc);
                default -> {}
            }
        }
}
