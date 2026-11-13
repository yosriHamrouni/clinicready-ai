package com.clinicready.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Extracted text of one document. Never logged; only exposed through {@link DocumentService#extractedText}. */
@Entity
@Table(name = "document_text")
class DocumentText {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "page_count", nullable = false, updatable = false)
    private int pageCount;

    @Column(nullable = false, updatable = false)
    private boolean truncated;

    protected DocumentText() {
    }

    DocumentText(UUID documentId, UUID clinicId, String content, int pageCount, boolean truncated) {
        this.documentId = documentId;
        this.clinicId = clinicId;
        this.content = content;
        this.pageCount = pageCount;
        this.truncated = truncated;
    }

    String getContent() {
        return content;
    }

    int getPageCount() {
        return pageCount;
    }

    boolean isTruncated() {
        return truncated;
    }
}
