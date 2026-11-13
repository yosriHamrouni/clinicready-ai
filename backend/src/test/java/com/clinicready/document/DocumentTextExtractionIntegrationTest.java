package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * Background text extraction against a real database. Not {@code @Transactional}: extraction starts
 * only after the upload commits, so the rows created here are deleted after each test.
 */
@IntegrationTest
class DocumentTextExtractionIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("clinicready.documents.storage-path", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("clinicready-extraction-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentTextExtraction extraction;

    private final List<UUID> clinics = new ArrayList<>();

    @AfterEach
    void deleteTestData() {
        for (UUID clinic : clinics) {
            jdbc.update("DELETE FROM document_text WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM document WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM patient WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM app_user WHERE clinic_id = ?", clinic);
            jdbc.update("DELETE FROM clinic WHERE id = ?", clinic);
        }
    }

    @Test
    void anUploadedPdfStartsPendingAndEndsExtractedWithItsTextStored() throws Exception {
        Fixture f = fixture();

        String body = mvc.perform(multipart("/api/v1/patients/{id}/documents", f.patient)
                        .file(new MockMultipartFile("file", "ordonnance.pdf", "application/pdf",
                                pdf("Ordonnance synthetique: Paracetamol 500 mg")))
                        .param("category", "PRESCRIPTION").with(f.user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractionStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        Map<String, Object> row = awaitFinished(id);

        assertThat(row.get("extraction_status")).isEqualTo("EXTRACTED");
        assertThat(row.get("extraction_failure")).isNull();
        assertThat(row.get("extracted_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT content FROM document_text WHERE document_id = ?", String.class, id))
                .contains("Paracetamol 500 mg");
        mvc.perform(get("/api/v1/documents/{id}", id).with(f.user))
                .andExpect(jsonPath("$.extractionStatus").value("EXTRACTED"))
                .andExpect(jsonPath("$.extractionFailure").doesNotExist());
    }

    @Test
    void anImageFailsExtractionButIsStillAnExistingDocument() throws Exception {
        Fixture f = fixture();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        String body = mvc.perform(multipart("/api/v1/patients/{id}/documents", f.patient)
                        .file(new MockMultipartFile("file", "carte.png", "image/png", png))
                        .param("category", "INSURANCE_CARD").with(f.user))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        Map<String, Object> row = awaitFinished(id);

        assertThat(row.get("extraction_status")).isEqualTo("EXTRACTION_FAILED");
        assertThat(row.get("extraction_failure")).isEqualTo("NO_TEXT_LAYER");
        assertThat(count("SELECT count(*) FROM document_text WHERE document_id = ?", id)).isZero();
        // Still listed for the patient: a failed extraction is not a missing document.
        mvc.perform(get("/api/v1/patients/{id}/documents", f.patient).with(f.user))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].extractionStatus").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$[0].extractionFailure").value("NO_TEXT_LAYER"));
    }

    @Test
    void aDocumentWhoseFileIsGoneIsMarkedFileMissing() {
        Fixture f = fixture();
        UUID id = insertPendingDocument(f, "does-not-exist/" + UUID.randomUUID());

        extraction.extract(id);

        assertThat(jdbc.queryForMap("SELECT extraction_status, extraction_failure FROM document WHERE id = ?", id))
                .containsEntry("extraction_status", "EXTRACTION_FAILED")
                .containsEntry("extraction_failure", "FILE_MISSING");
    }

    @Test
    void extractingAFinishedDocumentAgainChangesNothing() {
        Fixture f = fixture();
        UUID id = insertPendingDocument(f, "does-not-exist/" + UUID.randomUUID());
        extraction.extract(id);
        Object finishedAt = jdbc.queryForObject("SELECT extracted_at FROM document WHERE id = ?", Object.class, id);

        extraction.extract(id);

        assertThat(jdbc.queryForObject("SELECT extracted_at FROM document WHERE id = ?", Object.class, id))
                .isEqualTo(finishedAt);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private record Fixture(UUID clinic, UUID userId, UUID patient, RequestPostProcessor user) {
    }

    private Fixture fixture() {
        UUID clinic = UUID.randomUUID();
        clinics.add(clinic);
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Extraction Test')", clinic);
        UUID user = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', 'RECEPTIONIST')""",
                user, clinic, user + "@extraction-test.test");
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                patient, clinic, "X-" + patient.toString().substring(0, 8));
        RequestPostProcessor auth = jwt().jwt(j -> j.subject(user.toString())
                .claim("clinicId", clinic.toString())
                .claim("role", Role.RECEPTIONIST.name()));
        return new Fixture(clinic, user, patient, auth);
    }

    private UUID insertPendingDocument(Fixture f, String storageKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document (id, clinic_id, patient_id, category, source, version, original_filename,
                                      content_type, size_bytes, sha256, storage_key, uploaded_by)
                VALUES (?, ?, ?, 'OTHER', 'STAFF_UPLOAD', 1, 'doc.pdf', 'application/pdf', 10, ?, ?, ?)""",
                id, f.clinic, f.patient, "0".repeat(64), storageKey, f.userId);
        return id;
    }

    private Map<String, Object> awaitFinished(UUID id) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT extraction_status, extraction_failure, extracted_at FROM document WHERE id = ?", id);
            if (!"PENDING".equals(row.get("extraction_status"))) {
                return row;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document " + id + " still PENDING after " + TIMEOUT);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
