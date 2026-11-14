package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.clinicready.IntegrationTest;

/**
 * Classification against a real database with a scripted model. Not {@code @Transactional}: the job
 * commits its own transaction, so the rows created here are deleted after each test.
 */
@IntegrationTest
@Import(DocumentClassificationIntegrationTest.StubConfig.class)
class DocumentClassificationIntegrationTest {

    /** Replies with whatever the test sets; stands in for the Spring AI model. */
    static class StubModel implements ExtractionModel {
        volatile boolean available = true;
        volatile String reply;
        volatile boolean fail;
        volatile String lastDocumentText;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Answer complete(String systemPrompt, String documentText) {
            lastDocumentText = documentText;
            if (fail) {
                throw new IllegalStateException("provider down");
            }
            return new Answer(reply, "stub-model-1");
        }
    }

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        StubModel stubModel() {
            return new StubModel();
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentClassification classification;

    @Autowired
    private StubModel model;

    private final List<UUID> clinics = new ArrayList<>();

    @BeforeEach
    void resetModel() {
        model.available = true;
        model.fail = false;
        model.reply = null;
        model.lastDocumentText = null;
    }

    @AfterEach
    void deleteTestData() {
        for (UUID clinic : clinics) {
            jdbc.update("DELETE FROM document_extraction WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM document_text WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM document WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM patient WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM app_user WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM clinic WHERE id = ?", clinic);
        }
    }

    @Test
    void aConfidentAnswerIsStoredWithItsModelAndConfidenceAndNeedsNoReview() {
        UUID id = extractedDocument("Ordonnance du 2 novembre 2026, Dr Test");
        model.reply = """
                {"category":"PRESCRIPTION","documentDate":"2026-11-02","issuer":"Dr Test","confidence":0.93}""";

        classification.classify(id);

        Map<String, Object> row = extraction(id);
        assertThat(row).containsEntry("status", "SUCCEEDED")
                .containsEntry("category", "PRESCRIPTION")
                .containsEntry("issuer", "Dr Test")
                .containsEntry("model_name", "stub-model-1")
                .containsEntry("review_reason", null);
        assertThat(row.get("document_date")).hasToString("2026-11-02");
        assertThat(((Number) row.get("confidence")).doubleValue()).isEqualTo(0.93);
        assertThat(documentStatus(id)).isEqualTo("UPLOADED");
        assertThat(model.lastDocumentText).contains("Dr Test");
    }

    @Test
    void lowConfidenceIsKeptButFlaggedForReview() {
        UUID id = extractedDocument("texte flou");
        model.reply = """
                {"category":"REFERRAL_LETTER","documentDate":null,"issuer":null,"confidence":0.4}""";

        classification.classify(id);

        assertThat(extraction(id)).containsEntry("status", "SUCCEEDED")
                .containsEntry("category", "REFERRAL_LETTER")
                .containsEntry("review_reason", "LOW_CONFIDENCE");
        assertThat(documentStatus(id)).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void anUnknownCategoryStaysUnknownAndIsFlaggedEvenWhenConfident() {
        UUID id = extractedDocument("texte");
        model.reply = """
                {"category":null,"documentDate":null,"issuer":null,"confidence":0.95}""";

        classification.classify(id);

        assertThat(extraction(id)).containsEntry("category", null).containsEntry("review_reason", "CATEGORY_UNKNOWN");
        assertThat(documentStatus(id)).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void outputThatFailsTheSchemaMarksTheExtractionFailedAndFlagsTheDocument() {
        UUID id = extractedDocument("texte");
        model.reply = """
                {"category":"PASSPORT","documentDate":null,"issuer":null,"confidence":0.99}""";

        classification.classify(id);

        assertThat(extraction(id)).containsEntry("status", "FAILED")
                .containsEntry("failure", "INVALID_OUTPUT")
                .containsEntry("category", null)
                .containsEntry("confidence", null)
                .containsEntry("review_reason", "EXTRACTION_FAILED");
        assertThat(documentStatus(id)).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void aModelErrorMarksTheExtractionFailedAndFlagsTheDocument() {
        UUID id = extractedDocument("texte");
        model.fail = true;

        classification.classify(id);

        assertThat(extraction(id)).containsEntry("status", "FAILED")
                .containsEntry("failure", "MODEL_ERROR")
                .containsEntry("review_reason", "EXTRACTION_FAILED");
        assertThat(documentStatus(id)).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void withoutAModelNothingIsRecordedAndTheDocumentIsLeftAlone() {
        UUID id = extractedDocument("texte");
        model.available = false;

        classification.classify(id);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_extraction WHERE document_id = ?",
                Integer.class, id)).isZero();
        assertThat(documentStatus(id)).isEqualTo("UPLOADED");
    }

    @Test
    void aDocumentWhoseTextCouldNotBeReadIsNotSentToTheModel() {
        UUID id = extractedDocument("texte");
        jdbc.update("DELETE FROM document_text WHERE document_id = ?", id);
        model.reply = "{}";

        classification.classify(id);

        assertThat(model.lastDocumentText).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_extraction WHERE document_id = ?",
                Integer.class, id)).isZero();
    }

    @Test
    void classifyingAgainKeepsTheFirstObservation() {
        UUID id = extractedDocument("texte");
        model.reply = """
                {"category":"CONSENT_FORM","documentDate":null,"issuer":null,"confidence":0.9}""";
        classification.classify(id);
        model.reply = """
                {"category":"LAB_RESULT","documentDate":null,"issuer":null,"confidence":0.9}""";

        classification.classify(id);

        assertThat(extraction(id)).containsEntry("category", "CONSENT_FORM");
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private Map<String, Object> extraction(UUID id) {
        return jdbc.queryForMap("SELECT * FROM document_extraction WHERE document_id = ?", id);
    }

    private String documentStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM document WHERE id = ?", String.class, id);
    }

    /** A document whose text has already been read, as it is when classification starts. */
    private UUID extractedDocument(String text) {
        UUID clinic = UUID.randomUUID();
        clinics.add(clinic);
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Classification Test')", clinic);
        UUID user = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', 'RECEPTIONIST')""",
                user, clinic, user + "@classification-test.test");
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                patient, clinic, "C-" + patient.toString().substring(0, 8));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document (id, clinic_id, patient_id, category, source, version, original_filename,
                                      content_type, size_bytes, sha256, storage_key, uploaded_by,
                                      extraction_status, extracted_at)
                VALUES (?, ?, ?, 'OTHER', 'STAFF_UPLOAD', 1, 'doc.pdf', 'application/pdf', 10, ?, ?, ?,
                        'EXTRACTED', now())""",
                id, clinic, patient, "0".repeat(64), "classification-test/" + id, user);
        jdbc.update("INSERT INTO document_text (document_id, clinic_id, content, page_count, truncated) "
                + "VALUES (?, ?, ?, 1, false)", id, clinic, text);
        return id;
    }
}
