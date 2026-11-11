package com.clinicready.document;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param storagePath directory for uploaded files; mount a volume here in production
 * @param maxSize     largest accepted file; also drives Spring's multipart limit (see application.yml)
 */
@ConfigurationProperties("clinicready.documents")
record DocumentProperties(
        @DefaultValue("./storage/documents") Path storagePath,
        @DefaultValue("10MB") DataSize maxSize) {
}
