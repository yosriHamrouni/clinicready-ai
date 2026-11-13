package com.clinicready.document;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param storagePath directory for uploaded files; mount a volume here in production
 * @param maxSize     largest accepted file; also drives Spring's multipart limit (see application.yml)
 * @param extraction  background text extraction
 */
@ConfigurationProperties("clinicready.documents")
record DocumentProperties(
        @DefaultValue("./storage/documents") Path storagePath,
        @DefaultValue("10MB") DataSize maxSize,
        @DefaultValue Extraction extraction) {

    /**
     * @param threads       worker threads
     * @param maxPages      pages read per PDF; the rest is dropped and the text marked truncated
     * @param maxChars      characters stored per document; same
     * @param sweepInterval how often documents stuck in PENDING are re-queued
     * @param staleAfter    how long a document may stay PENDING before the sweep re-queues it
     */
    record Extraction(
            @DefaultValue("2") int threads,
            @DefaultValue("100") int maxPages,
            @DefaultValue("200000") int maxChars,
            @DefaultValue("PT5M") Duration sweepInterval,
            @DefaultValue("PT2M") Duration staleAfter) {
    }
}
