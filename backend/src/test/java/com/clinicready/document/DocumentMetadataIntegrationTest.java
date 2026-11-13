package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;

/** Reading and correcting a document's extracted fields through PATCH /documents/{id}/metadata. */
@IntegrationTest
@Transactional
class DocumentMetadataIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aDocumentWithNoEditsHasEmptyFieldsAndNoEditor() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID doc = document(a, patient(a), reception);

        mvc.perform(get("/api/v1/documents/{id}/metadata", doc).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(doc.toString()))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void receptionistCorrectsFieldsAndTheEditorAndTimestampAreSaved() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID doc = document(a, patient(a), reception);
        Instant before = Instant.now().minusSeconds(1);

        edit(reception, doc, "{\"fields\":{\"expiryDate\":\"2027-03-01\",\"issuer\":\"  CNAM  \"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.expiryDate").value("2027-03-01"))
                .andExpect(jsonPath("$.fields.issuer").value("CNAM"))
                .andExpect(jsonPath("$.updatedBy").value(reception.id.toString()))
                .andExpect(jsonPath("$.updatedByName").value("User Test"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Timestamp savedAt = jdbc.queryForObject("SELECT updated_at FROM document_metadata WHERE document_id = ?",
                Timestamp.class, doc);
        assertThat(savedAt.toInstant()).isAfter(before);

        // A second edit by someone else merges, keeps untouched fields and replaces editor.
        User manager = user(a, Role.CLINIC_MANAGER, null);
        edit(manager, doc, "{\"fields\":{\"documentNumber\":\"A-123\",\"issuer\":null}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.expiryDate").value("2027-03-01"))
                .andExpect(jsonPath("$.fields.documentNumber").value("A-123"))
                .andExpect(jsonPath("$.fields.issuer").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").value(manager.id.toString()));

        mvc.perform(get("/api/v1/documents/{id}/metadata", doc).with(as(reception)))
                .andExpect(jsonPath("$.fields.documentNumber").value("A-123"))
                .andExpect(jsonPath("$.updatedBy").value(manager.id.toString()));
    }

    @Test
    void anEditIsAuditedWithFieldNamesButNeverValues() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID doc = document(a, patient(a), reception);

        edit(reception, doc, "{\"fields\":{\"holderName\":\"Secret Name\"}}").andExpect(status().isOk());

        entityManager.flush();
        String summary = jdbc.queryForObject("""
                SELECT change_summary::text FROM audit_event
                WHERE entity_id = ? AND action = 'DOCUMENT_METADATA_UPDATED' AND actor_user_id = ?""",
                String.class, doc, reception.id);
        assertThat(summary).contains("holderName").doesNotContain("Secret Name");
    }

    @Test
    void resavingTheSameValuesChangesNothingAndWritesNoAuditEvent() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID doc = document(a, patient(a), reception);
        String body = "{\"fields\":{\"issuer\":\"CNAM\"}}";

        edit(reception, doc, body).andExpect(status().isOk());
        edit(reception, doc, body).andExpect(status().isOk()).andExpect(jsonPath("$.fields.issuer").value("CNAM"));
        edit(reception, doc, "{\"fields\":{\"expiryDate\":\"\"}}").andExpect(status().isOk());

        assertThat(count("audit_event WHERE action = 'DOCUMENT_METADATA_UPDATED'")).isEqualTo(1);
    }

    @Test
    void rejectsUnknownFieldsBadDatesAndOverlongValuesWithoutSavingAnything() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID doc = document(a, patient(a), reception);

        edit(reception, doc, "{\"fields\":{\"status\":\"VERIFIED\"}}").andExpect(status().isBadRequest());
        edit(reception, doc, "{\"fields\":{\"expiryDate\":\"01/03/2027\"}}").andExpect(status().isBadRequest());
        edit(reception, doc, "{\"fields\":{\"expiryDate\":\"2027-02-30\"}}").andExpect(status().isBadRequest());
        edit(reception, doc, "{\"fields\":{\"issuer\":\"" + "x".repeat(201) + "\"}}").andExpect(status().isBadRequest());
        edit(reception, doc, "{\"fields\":{\"issuer\":\"ok\",\"documentDate\":\"nope\"}}")
                .andExpect(status().isBadRequest());
        edit(reception, doc, "{}").andExpect(status().isBadRequest());

        assertThat(count("document_metadata")).isZero();
        assertThat(count("audit_event WHERE action = 'DOCUMENT_METADATA_UPDATED'")).isZero();
    }

    @Test
    void practitionerOnlyEditsDocumentsOfTheirOwnPatients() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID practitionerId = practitioner(a);
        User doctor = user(a, Role.PRACTITIONER, practitionerId);
        UUID visiblePatient = patient(a);
        UUID otherPatient = patient(a);
        appointment(a, visiblePatient, practitionerId);
        UUID visible = document(a, visiblePatient, reception);
        UUID hidden = document(a, otherPatient, reception);

        edit(doctor, visible, "{\"fields\":{\"issuer\":\"CNAM\"}}").andExpect(status().isOk());
        edit(doctor, hidden, "{\"fields\":{\"issuer\":\"CNAM\"}}").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/{id}/metadata", hidden).with(as(doctor))).andExpect(status().isNotFound());
    }

    @Test
    void anotherClinicCannotReadOrEditAndUnknownDocumentsAre404() throws Exception {
        Clinic a = clinic();
        Clinic b = clinic();
        User receptionA = user(a, Role.RECEPTIONIST, null);
        User managerB = user(b, Role.CLINIC_MANAGER, null);
        UUID doc = document(a, patient(a), receptionA);

        mvc.perform(get("/api/v1/documents/{id}/metadata", doc).with(as(managerB))).andExpect(status().isNotFound());
        edit(managerB, doc, "{\"fields\":{\"issuer\":\"x\"}}").andExpect(status().isNotFound());
        edit(receptionA, UUID.randomUUID(), "{\"fields\":{\"issuer\":\"x\"}}").andExpect(status().isNotFound());
        assertThat(count("document_metadata")).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/documents/{id}/metadata", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private ResultActions edit(User as, UUID document, String json) throws Exception {
        return mvc.perform(patch("/api/v1/documents/{id}/metadata", document)
                .contentType(MediaType.APPLICATION_JSON).content(json).with(as(as)));
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(j -> {
            j.subject(user.id.toString());
            j.claim("clinicId", user.clinic.id.toString());
            j.claim("role", user.role.name());
            if (user.practitionerId != null) {
                j.claim("practitionerId", user.practitionerId.toString());
            }
        });
    }

    private record Clinic(UUID id) {
    }

    private record User(UUID id, Clinic clinic, Role role, UUID practitionerId) {
    }

    private Clinic clinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", id);
        return new Clinic(id);
    }

    private User user(Clinic clinic, Role role, UUID practitionerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, practitioner_id)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?, ?)""",
                id, clinic.id, id + "@metadata-test.test", role.name(), practitionerId);
        return new User(id, clinic, role, practitionerId);
    }

    private UUID practitioner(Clinic clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", id, clinic.id);
        return id;
    }

    private UUID patient(Clinic clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                id, clinic.id, "T-" + id.toString().substring(0, 8));
        return id;
    }

    private void appointment(Clinic clinic, UUID patient, UUID practitioner) {
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'T', 'Test')",
                type, clinic.id);
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), clinic.id, patient, practitioner, type, Timestamp.from(Instant.now()));
    }

    /** Inserts document metadata directly; the file itself is not needed for these tests. */
    private UUID document(Clinic clinic, UUID patient, User uploader) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document (id, clinic_id, patient_id, category, source, version, original_filename,
                                      content_type, size_bytes, sha256, storage_key, uploaded_by)
                VALUES (?, ?, ?, 'OTHER', 'STAFF_UPLOAD', 1, 'doc.pdf', 'application/pdf', 10, ?, ?, ?)""",
                id, clinic.id, patient, "a".repeat(64), clinic.id + "/" + id, uploader.id);
        return id;
    }

    private int count(String tableAndFilter) {
        entityManager.flush(); // audit events are saved without a flush; make them visible to raw SQL
        return jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Integer.class);
    }
}
