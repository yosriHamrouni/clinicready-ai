package com.clinicready.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param minConfidence  below this the observation is kept but the document is flagged for review
 * @param maxPromptChars characters of document text sent to the model; the rest is dropped
 */
@ConfigurationProperties("clinicready.documents.classification")
record ClassificationProperties(
        @DefaultValue("0.7") double minConfidence,
        @DefaultValue("12000") int maxPromptChars) {
}
