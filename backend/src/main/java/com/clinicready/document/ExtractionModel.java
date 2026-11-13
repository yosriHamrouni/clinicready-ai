package com.clinicready.document;

/** The language model behind document classification. Behind an interface so tests need no model. */
interface ExtractionModel {

    /** What the model said and which model said it. {@code text} may be anything: the caller validates it. */
    record Answer(String text, String modelName) {
    }

    /** False when no model is configured (see {@code spring.ai.model.chat}); documents are then left unclassified. */
    boolean available();

    /** @throws RuntimeException on any provider failure */
    Answer complete(String systemPrompt, String documentText);
}
