package com.clinicready.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.clinicready.document.TextExtractor.Extracted;
import com.clinicready.document.TextExtractor.Failed;
import com.clinicready.document.TextExtractor.Result;

/**
 * Extracts text from uploaded documents in the background, moving each one from
 * {@code PENDING} to {@code EXTRACTED} or {@code EXTRACTION_FAILED} exactly once.
 *
 * <p>An upload is queued after its transaction commits. Documents left {@code PENDING} (a restart, a
 * full queue, a storage error) are picked up again at startup and by a periodic sweep. Two workers on
 * the same document are harmless: only the first one to finish records a result.
 *
 * <p>This is a system job, not a user request, so it reads documents by id without a clinic filter.
 * It logs ids, statuses and sizes only: never file names, text or exception messages from the database
 * (which can echo row values).
 */
@Component
class DocumentTextExtraction {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtraction.class);
    private static final int SWEEP_BATCH = 200;

    private final DocumentRepository documents;
    private final DocumentTextRepository texts;
    private final DocumentStorage storage;
    private final DocumentProperties properties;
    private final TextExtractor extractor;
    private final TransactionTemplate transaction;
    private final Executor executor;

    DocumentTextExtraction(DocumentRepository documents, DocumentTextRepository texts, DocumentStorage storage,
            DocumentProperties properties, TextExtractor extractor, TransactionTemplate transaction,
            @Qualifier(DocumentConfig.EXTRACTION_EXECUTOR) Executor executor) {
        this.documents = documents;
        this.texts = texts;
        this.storage = storage;
        this.properties = properties;
        this.extractor = extractor;
        this.transaction = transaction;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUploaded(DocumentUploaded event) {
        submit(event.documentId());
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverAfterStartup() {
        requeuePendingUploadedBefore(Instant.now());
    }

    @Scheduled(fixedDelayString = "${clinicready.documents.extraction.sweep-interval:PT5M}",
            initialDelayString = "${clinicready.documents.extraction.sweep-interval:PT5M}")
    void sweep() {
        requeuePendingUploadedBefore(Instant.now().minus(properties.extraction().staleAfter()));
    }

    private void requeuePendingUploadedBefore(Instant before) {
        var ids = documents.findPendingExtraction(before, Limit.of(SWEEP_BATCH));
        if (!ids.isEmpty()) {
            log.info("Re-queueing {} document(s) pending text extraction", ids.size());
            ids.forEach(this::submit);
        }
    }

    /** Queues one document. Never throws: a rejected task stays PENDING for the next sweep. */
    void submit(UUID documentId) {
        try {
            executor.execute(() -> extract(documentId));
        } catch (TaskRejectedException e) {
            log.warn("Extraction queue full; document {} stays pending until the next sweep", documentId);
        }
    }

    /** Runs the extraction for one document now. Package-visible for tests. */
    void extract(UUID documentId) {
        try {
            Document document = documents.findById(documentId).orElse(null);
            if (document == null || document.getExtractionStatus() != ExtractionStatus.PENDING) {
                return;
            }
            Result result = read(document);
            record(document, result);
        } catch (IOException e) {
            log.warn("Could not read stored file of document {} ({}); will retry", documentId,
                    e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.error("Text extraction of document {} failed unexpectedly ({}); will retry", documentId,
                    e.getClass().getName());
        }
    }

    private Result read(Document document) throws IOException {
        byte[] bytes;
        try (InputStream in = storage.open(document.getStorageKey())) {
            bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, properties.maxSize().toBytes()));
        } catch (NoSuchFileException e) {
            return new Failed(ExtractionFailure.FILE_MISSING);
        }
        try {
            return extractor.extract(document.getContentType(), bytes);
        } catch (RuntimeException e) {
            return new Failed(ExtractionFailure.INTERNAL_ERROR);
        }
    }

    private void record(Document document, Result result) {
        UUID id = document.getId();
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            switch (result) {
                case Extracted text -> {
                    if (documents.markExtracted(id, now) == 1) {
                        texts.save(new DocumentText(id, document.getClinicId(), text.text(), text.pageCount(),
                                text.truncated()));
                        log.info("Document {} text extracted: pages={} chars={} truncated={}", id,
                                text.pageCount(), text.text().length(), text.truncated());
                    }
                }
                case Failed failed -> {
                    if (documents.markExtractionFailed(id, failed.reason().name(), now) == 1) {
                        log.info("Document {} text extraction failed: {}", id, failed.reason());
                    }
                }
            }
        });
    }
}
