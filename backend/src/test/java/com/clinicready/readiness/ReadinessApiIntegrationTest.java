package com.clinicready.readiness;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/** Readiness API: run and read checks, evidence references, recalculation triggers and access scoping. */
@IntegrationTest
@Transactional
class ReadinessApiIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final byte[] PDF = ("%PDF-1.4\n% synthetic test document\n").getBytes();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("clinicready.documents.storage-path", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("clinicready-readiness-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void readinessIsRecalculatedAfterEachInputChangeAndLinksItsEvidence() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER, null);
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);
        UUID type = appointmentType(a);
        String ruleBody = mvc.perform(post("/api/v1/appointment-types/{id}/requirements", type)
                        .contentType(MediaType.APPLICATION_JSON).with(as(manager)).content("""
                                {"documentCategory":"CONSENT_FORM","mandatory":true,"validityDays":null,
                                 "needsVerification":false}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID rule = UUID.fromString(JsonPath.read(ruleBody, "$.id"));

        // Created: calculated straight away. Incompleteness and review reasons are both kept.
        UUID appointment = createAppointment(reception, patient, practitioner(a), type);
        readiness(reception, appointment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.notice").value(containsString("not a medical decision")))
                .andExpect(jsonPath("$.incompleteReasons.length()").value(1))
                .andExpect(jsonPath("$.reviewReasons.length()").value(2))
                .andExpect(jsonPath("$.checks[0].ruleId").value(rule.toString()))
                .andExpect(jsonPath("$.checks[0].requirement").value("DOCUMENT"))
                .andExpect(jsonPath("$.checks[0].documentCategories[0]").value("CONSENT_FORM"))
                .andExpect(jsonPath("$.checks[0].mandatory").value(true))
                .andExpect(jsonPath("$.checks[0].applicable").value(true))
                .andExpect(jsonPath("$.checks[0].status").value("MISSING"))
                .andExpect(jsonPath("$.checks[0].remainingAction").value("Obtain and upload a CONSENT_FORM document"))
                .andExpect(jsonPath("$.checks[1].requirement").value("EXPECTED_EXAMINATIONS"))
                .andExpect(jsonPath("$.checks[1].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.checks[2].requirement").value("ADVANCE_PAYMENT"))
                .andExpect(jsonPath("$.checks[2].remainingAction").value("Enter the advance amount"));

        // Upload: the document is evidence, but its text is not read yet.
        String uploaded = mvc.perform(multipart("/api/v1/patients/{id}/documents", patient)
                        .file(new MockMultipartFile("file", "consent.pdf", "application/pdf", PDF))
                        .param("category", "CONSENT_FORM").with(as(reception)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID document = UUID.fromString(JsonPath.read(uploaded, "$.id"));
        readiness(reception, appointment)
                .andExpect(jsonPath("$.checks[0].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.checks[0].requiresHumanReview").value(true))
                .andExpect(jsonPath("$.checks[0].documents[0].documentId").value(document.toString()))
                .andExpect(jsonPath("$.checks[0].documents[0].version").value(1));

        // On demand: picks up a change no event announced.
        entityManager.flush();
        jdbc.update("UPDATE document SET extraction_status = 'EXTRACTED', extracted_at = now() WHERE id = ?",
                document);
        entityManager.clear();
        mvc.perform(post("/api/v1/appointments/{id}/readiness-checks", appointment).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks[0].status").value("PRESENT"))
                .andExpect(jsonPath("$.checks[0].remainingAction").doesNotExist())
                .andExpect(jsonPath("$.checks[0].documents[0].documentId").value(document.toString()));

        // Administrative data, then the payment confirmation by a named staff member.
        mvc.perform(put("/api/v1/appointments/{id}/admin-details", appointment).with(as(reception))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedExaminations\":[],\"advanceAmount\":100}"))
                .andExpect(status().isOk());
        readiness(reception, appointment)
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.checks[1].status").value("PRESENT"))
                .andExpect(jsonPath("$.checks[2].remainingAction").value("Confirm the advance payment once received"))
                .andExpect(jsonPath("$.checks[2].confirmations").isEmpty());
        mvc.perform(post("/api/v1/appointments/{id}/payment-confirmation", appointment).with(as(reception)))
                .andExpect(status().isOk());
        readiness(reception, appointment)
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.incompleteReasons").isEmpty())
                .andExpect(jsonPath("$.reviewReasons").isEmpty())
                .andExpect(jsonPath("$.checks[2].status").value("PRESENT"))
                .andExpect(jsonPath("$.checks[2].documents").isEmpty())
                .andExpect(jsonPath("$.checks[2].confirmations[0].type").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.checks[2].confirmations[0].confirmedBy").value(reception.id.toString()))
                .andExpect(jsonPath("$.checks[2].confirmations[0].confirmedByName").value("User Test"))
                .andExpect(jsonPath("$.checks[2].confirmations[0].confirmedAt").isNotEmpty());

        // A metadata correction: the corrected expiry date is before the appointment.
        mvc.perform(patch("/api/v1/documents/{id}/metadata", document).with(as(reception))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"expiryDate\":\"2020-01-01\"}}"))
                .andExpect(status().isOk());
        readiness(reception, appointment)
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.checks[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.checks[0].remainingAction").value("Obtain a valid CONSENT_FORM document"));

        // A rule change: the requirement becomes optional, so it no longer blocks.
        mvc.perform(put("/api/v1/requirements/{id}", rule).with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentCategory":"CONSENT_FORM","mandatory":false,"validityDays":null,
                                 "needsVerification":false}"""))
                .andExpect(status().isOk());
        readiness(reception, appointment)
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.checks[0].mandatory").value(false))
                .andExpect(jsonPath("$.checks[0].status").value("EXPIRED"));
    }

    @Test
    void readinessIsVisibleOnlyToUsersWhoMaySeeTheAppointment() throws Exception {
        Clinic a = clinic();
        UUID ownPractitioner = practitioner(a);
        User own = user(a, Role.PRACTITIONER, ownPractitioner);
        User other = user(a, Role.PRACTITIONER, practitioner(a));
        User reception = user(a, Role.RECEPTIONIST, null);
        User stranger = user(clinic(), Role.CLINIC_MANAGER, null);
        UUID appointment = insertAppointment(a, patient(a), ownPractitioner, appointmentType(a));

        // Never checked yet (inserted without the service): no result until someone runs it.
        readiness(reception, appointment).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/appointments/{id}/readiness-checks", appointment).with(as(own)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(appointment.toString()))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));

        // Read back from the database, not the persistence context.
        entityManager.flush();
        entityManager.clear();
        readiness(own, appointment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.length()").value(2))
                .andExpect(jsonPath("$.checks[1].requirement").value("ADVANCE_PAYMENT"))
                .andExpect(jsonPath("$.evaluatedAt").isNotEmpty());
        readiness(reception, appointment).andExpect(status().isOk());

        for (User u : new User[] {other, stranger}) {
            readiness(u, appointment).andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/appointments/{id}/readiness-checks", appointment).with(as(u)))
                    .andExpect(status().isNotFound());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private ResultActions readiness(User as, UUID appointment) throws Exception {
        return mvc.perform(get("/api/v1/appointments/{id}/readiness", appointment).with(as(as)));
    }

    private UUID createAppointment(User as, UUID patient, UUID practitioner, UUID type) throws Exception {
        String json = "{\"patientId\":\"%s\",\"practitionerId\":\"%s\",\"appointmentTypeId\":\"%s\",\"startsAt\":\"%s\"}"
                .formatted(patient, practitioner, type, tomorrow());
        String body = mvc.perform(post("/api/v1/appointments").with(as(as))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static Instant tomorrow() {
        return Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
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
                id, clinic.id, id + "@readiness-test.test", role.name(), practitionerId);
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

    private UUID appointmentType(Clinic clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, ?, 'Test')",
                id, clinic.id, "T-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertAppointment(Clinic clinic, UUID patient, UUID practitioner, UUID type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                id, clinic.id, patient, practitioner, type, Timestamp.from(tomorrow()));
        return id;
    }
}
