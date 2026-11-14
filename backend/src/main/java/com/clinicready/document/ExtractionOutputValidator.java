package com.clinicready.document;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Turns the model's raw answer into validated observations, or rejects it. Plain Java, no Spring.
 *
 * <p>Whatever the model says is untrusted: it must parse as JSON and match
 * {@code extraction/document-extraction.schema.json}, and the date must be a real calendar date.
 * Anything else is {@link Invalid}; nothing is repaired or guessed.
 */
final class ExtractionOutputValidator {

    static final String SCHEMA_RESOURCE = "/extraction/document-extraction.schema.json";

    sealed interface Result {
    }

    /** Schema-valid values. A null category, date or issuer means the model could not identify it. */
    record Valid(DocumentCategory category, LocalDate documentDate, String issuer, double confidence)
            implements Result {
    }

    record Invalid() implements Result {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema;

    ExtractionOutputValidator() {
        try (InputStream in = ExtractionOutputValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + SCHEMA_RESOURCE);
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + SCHEMA_RESOURCE, e);
        }
    }

    Result validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Invalid();
        }
        JsonNode node;
        try {
            node = mapper.readTree(stripCodeFence(raw.trim()));
        } catch (IOException e) {
            return new Invalid();
        }
        if (node == null || !node.isObject()) {
            return new Invalid();
        }
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            return new Invalid();
        }
        DocumentCategory category = node.get("category").isNull()
                ? null : DocumentCategory.valueOf(node.get("category").asText());
        LocalDate date = null;
        if (!node.get("documentDate").isNull()) {
            try {
                date = LocalDate.parse(node.get("documentDate").asText());
            } catch (DateTimeParseException e) {
                return new Invalid(); // matches the pattern but is not a real date, e.g. 2026-02-30
            }
        }
        String issuer = node.get("issuer").isNull() ? null : node.get("issuer").asText().trim();
        if (issuer != null && issuer.isEmpty()) {
            issuer = null;
        }
        return new Valid(category, date, issuer, node.get("confidence").asDouble());
    }

    /** Models often wrap JSON in a markdown fence; that wrapper is the only thing tolerated. */
    private static String stripCodeFence(String text) {
        if (text.startsWith("```") && text.endsWith("```") && text.length() >= 6) {
            String inner = text.substring(3, text.length() - 3);
            int newline = inner.indexOf('\n');
            if (newline >= 0 && !inner.substring(0, newline).contains("{")) {
                return inner.substring(newline + 1); // drop a language tag such as "json"
            }
            return inner;
        }
        return text;
    }
}
