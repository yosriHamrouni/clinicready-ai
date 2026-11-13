package com.clinicready.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * Patient and appointment API through the HTTP layer: CRUD, filters, clinic scoping, role
 * permissions and the administrative checklist data of an appointment.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class PatientAndAppointmentApiIntegrationTest {

    private static final Instant DAY = Instant.parse("2026-11-20T00:00:00Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-11-21T00:00:00Z");
    private static final Path STORAGE_ROOT = createStorageRoot();

    private static final String FULL_DETAILS = """
            {"trustedContact":{"name":" Sami Test ","method":"PHONE","value":"+216 20 000 000"},
             "expectedExaminations":["Bilan sanguin"," IRM genou "],
             "advanceAmount":150.5}""";

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("clinicready.documents.storage-path", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("clinicready-appointments-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @PersistenceContext
    private EntityManager entityManager;

    private Tenant a;
    private Tenant b;
    private User reception;
    private User manager;
    private User doctor;

    @BeforeEach
    void seed() {
        a = tenant();
        b = tenant();
        reception = user(a, Role.RECEPTIONIST, null);
        manager = user(a, Role.CLINIC_MANAGER, null);
        doctor = user(a, Role.PRACTITIONER, a.practitioner);
    }

    // ---- patients --------------------------------------------------------------------------------

    @Test
    void receptionistCreatesPatientsWithGeneratedReferencesAndFindsThem() throws Exception {
        perform(post("/api/v1/patients"), reception, """
                {"fullName":" Amel Test ","phone":"+216 20 123 456","preferredLanguage":"AR"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/patients/")))
                .andExpect(jsonPath("$.patientRef").value("P-0001"))
                .andExpect(jsonPath("$.fullName").value("Amel Test"))
                .andExpect(jsonPath("$.preferredLanguage").value("AR"));
        String body = perform(post("/api/v1/patients"), manager, "{\"fullName\":\"Bechir Test\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientRef").value("P-0002"))
                .andExpect(jsonPath("$.preferredLanguage").value("FR"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID bechir = UUID.fromString(JsonPath.read(body, "$.id"));

        mvc.perform(get("/api/v1/patients").param("q", "bech").with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].patientRef").value(contains("P-0002")));
        mvc.perform(get("/api/v1/patients").param("q", "p-000").with(as(reception)))
                .andExpect(jsonPath("$[*].patientRef").value(contains("P-0001", "P-0002")));
        mvc.perform(get("/api/v1/patients").param("q", "%").with(as(reception)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/patients/{id}", bechir).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Bechir Test"));
        assertThat(count("audit_event WHERE action = 'PATIENT_CREATED' AND clinic_id = '" + a.clinic + "'"))
                .isEqualTo(2);
    }

    @Test
    void patientCreationIsValidatedAndReferencesAreUniquePerClinic() throws Exception {
        for (String invalid : List.of("{}", "{\"fullName\":\"  \"}",
                "{\"fullName\":\"X\",\"phone\":\"call me\"}",
                "{\"fullName\":\"X\",\"patientRef\":\"has space\"}",
                "{\"fullName\":\"X\",\"preferredLanguage\":\"EN\"}")) {
            perform(post("/api/v1/patients"), reception, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        String withRef = "{\"fullName\":\"X\",\"patientRef\":\"P-NEW\"}";
        perform(post("/api/v1/patients"), reception, withRef).andExpect(status().isCreated());
        perform(post("/api/v1/patients"), reception, withRef)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_REF_CONFLICT"));
        // The same reference is free in another clinic.
        perform(post("/api/v1/patients"), user(b, Role.RECEPTIONIST, null), withRef)
                .andExpect(status().isCreated());
    }

    @Test
    void practitionersReadOnlyTheirOwnPatientsAndCannotCreateAny() throws Exception {
        UUID stranger = patient(a, "P-OTHER");

        mvc.perform(get("/api/v1/patients").with(as(doctor)))
                .andExpect(jsonPath("$[*].id").value(contains(a.patient.toString())));
        mvc.perform(get("/api/v1/patients/{id}", stranger).with(as(doctor))).andExpect(status().isNotFound());
        perform(post("/api/v1/patients"), doctor, "{\"fullName\":\"X\"}").andExpect(status().isForbidden());
    }

    @Test
    void anotherClinicsPatientIsNotFoundAndNotListed() throws Exception {
        mvc.perform(get("/api/v1/patients/{id}", b.patient).with(as(manager))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/patients").with(as(manager)))
                .andExpect(jsonPath("$[*].id").value(contains(a.patient.toString())));
    }

    // ---- appointments ----------------------------------------------------------------------------

    @Test
    void receptionistCreatesAnAppointmentAndReadsItWithItsRelatedRows() throws Exception {
        String body = perform(post("/api/v1/appointments"), reception, newAppointment(a, "2026-11-20T09:30:00Z"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.patient.patientRef").value("P-A"))
                .andExpect(jsonPath("$.practitioner.id").value(a.practitioner.toString()))
                .andExpect(jsonPath("$.appointmentType.code").value("CONSULT"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        mvc.perform(get("/api/v1/appointments/{id}", id).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-11-20T09:30:00Z"))
                .andExpect(jsonPath("$.adminDetails.trustedContact.status").value("MISSING"))
                .andExpect(jsonPath("$.adminDetails.expectedExaminations.status").value("MISSING"))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("MISSING"))
                .andExpect(jsonPath("$.adminDetails.advance.currency").value("TND"));
        assertThat(count("audit_event WHERE action = 'APPOINTMENT_CREATED' AND entity_id = '" + id + "'"))
                .isEqualTo(1);
        assertThat(events.stream(AppointmentChecklistChanged.class)).extracting(AppointmentChecklistChanged::appointmentId)
                .containsExactly(id);
    }

    @Test
    void appointmentCreationIsValidatedAndScopedToTheClinic() throws Exception {
        perform(post("/api/v1/appointments"), reception, "{\"patientId\":\"" + a.patient + "\"}")
                .andExpect(status().isBadRequest());
        perform(post("/api/v1/appointments"), reception, newAppointment(a, "2026-11-20T09:30:00Z")
                .replace(a.patient.toString(), b.patient.toString()))
                .andExpect(status().isNotFound());
        perform(post("/api/v1/appointments"), reception, newAppointment(a, "2026-11-20T09:30:00Z")
                .replace(a.practitioner.toString(), b.practitioner.toString()))
                .andExpect(status().isNotFound());
        perform(post("/api/v1/appointments"), doctor, newAppointment(a, "2026-11-20T09:30:00Z"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/appointments/{id}", b.appointment).with(as(manager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByDateRangePractitionerStatusAndPatient() throws Exception {
        UUID otherPractitioner = practitioner(a);
        UUID morning = a.appointment; // 08:00, SCHEDULED, a.practitioner
        UUID cancelled = appointment(a, a.patient, otherPractitioner, DAY.plusSeconds(10 * 3600), "CANCELLED");
        UUID nextDay = appointment(a, a.patient, a.practitioner, NEXT_DAY.plusSeconds(3600), "SCHEDULED");

        mvc.perform(list(reception).param("from", DAY.toString()).param("to", NEXT_DAY.toString()))
                .andExpect(jsonPath("$[*].id").value(contains(morning.toString(), cancelled.toString())));
        mvc.perform(list(reception).param("from", DAY.toString()).param("to", NEXT_DAY.toString())
                        .param("practitionerId", otherPractitioner.toString()))
                .andExpect(jsonPath("$[*].id").value(contains(cancelled.toString())));
        mvc.perform(list(reception).param("from", DAY.toString()).param("to", NEXT_DAY.plusSeconds(86400).toString())
                        .param("status", "SCHEDULED"))
                .andExpect(jsonPath("$[*].id").value(contains(morning.toString(), nextDay.toString())));
        mvc.perform(list(reception).param("status", "CANCELLED,NO_SHOW").param("patientId", a.patient.toString()))
                .andExpect(jsonPath("$[*].id").value(contains(cancelled.toString())));
    }

    @Test
    void listRejectsUnboundedOrInvalidFilters() throws Exception {
        for (MockHttpServletRequestBuilder invalid : List.of(
                list(reception),
                list(reception).param("from", DAY.toString()),
                list(reception).param("from", NEXT_DAY.toString()).param("to", DAY.toString()),
                list(reception).param("from", DAY.toString()).param("to", DAY.plusSeconds(93L * 86400).toString()),
                list(reception).param("from", "tomorrow").param("to", NEXT_DAY.toString()),
                list(reception).param("patientId", a.patient.toString()).param("status", "LATE"))) {
            mvc.perform(invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void practitionersListOnlyTheirOwnAppointmentsWhateverTheFilter() throws Exception {
        UUID otherPractitioner = practitioner(a);
        UUID notMine = appointment(a, a.patient, otherPractitioner, DAY.plusSeconds(3 * 3600), "SCHEDULED");

        mvc.perform(list(doctor).param("from", DAY.toString()).param("to", NEXT_DAY.toString()))
                .andExpect(jsonPath("$[*].id").value(contains(a.appointment.toString())));
        mvc.perform(list(doctor).param("from", DAY.toString()).param("to", NEXT_DAY.toString())
                        .param("practitionerId", otherPractitioner.toString()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/appointments/{id}", notMine).with(as(doctor))).andExpect(status().isNotFound());
    }

    @Test
    void everyRoleListsTheClinicsPractitioners() throws Exception {
        for (User u : List.of(reception, manager, doctor)) {
            mvc.perform(get("/api/v1/practitioners").with(as(u)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(contains(a.practitioner.toString())));
        }
    }

    // ---- administrative checklist data -----------------------------------------------------------

    @Test
    void staffEntersAdminDetailsWhichAreProvidedButNotConfirmed() throws Exception {
        adminDetails(reception, a.appointment, FULL_DETAILS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminDetails.trustedContact.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.trustedContact.name").value("Sami Test"))
                .andExpect(jsonPath("$.adminDetails.trustedContact.method").value("PHONE"))
                .andExpect(jsonPath("$.adminDetails.trustedContact.confirmation").doesNotExist())
                .andExpect(jsonPath("$.adminDetails.expectedExaminations.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.expectedExaminations.items")
                        .value(contains("Bilan sanguin", "IRM genou")))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.advance.amount").value(150.5))
                .andExpect(jsonPath("$.adminDetails.advance.paymentConfirmation").doesNotExist());

        // An empty list is a statement ("none expected"), not missing data.
        adminDetails(reception, a.appointment, "{\"expectedExaminations\":[]}")
                .andExpect(jsonPath("$.adminDetails.expectedExaminations.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.expectedExaminations.items", hasSize(0)))
                .andExpect(jsonPath("$.adminDetails.trustedContact.status").value("MISSING"))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("MISSING"));
    }

    @Test
    void confirmationsRecordTheActorAndAreClearedWhenTheirValueChanges() throws Exception {
        adminDetails(reception, a.appointment, FULL_DETAILS).andExpect(status().isOk());

        mvc.perform(post("/api/v1/appointments/{id}/trusted-contact-confirmation", a.appointment).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminDetails.trustedContact.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.adminDetails.trustedContact.confirmation.by").value(reception.id.toString()))
                .andExpect(jsonPath("$.adminDetails.trustedContact.confirmation.at").isNotEmpty());
        mvc.perform(post("/api/v1/appointments/{id}/payment-confirmation", a.appointment).with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminDetails.advance.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.adminDetails.advance.paymentConfirmation.by").value(manager.id.toString()));

        // Same values again: nothing changes, confirmations stay.
        adminDetails(reception, a.appointment, FULL_DETAILS)
                .andExpect(jsonPath("$.adminDetails.trustedContact.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("CONFIRMED"));

        adminDetails(reception, a.appointment, FULL_DETAILS.replace("+216 20 000 000", "+216 20 000 001")
                .replace("150.5", "200"))
                .andExpect(jsonPath("$.adminDetails.trustedContact.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.advance.amount").value(200));

        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_id = ? ORDER BY occurred_at",
                String.class, a.appointment)).containsExactly(
                        "APPOINTMENT_ADMIN_DETAILS_UPDATED", "TRUSTED_CONTACT_CONFIRMED", "PAYMENT_CONFIRMED",
                        "APPOINTMENT_ADMIN_DETAILS_UPDATED");
        String lastSummary = jdbc.queryForObject("""
                SELECT change_summary::text FROM audit_event WHERE entity_id = ? ORDER BY occurred_at DESC LIMIT 1""",
                String.class, a.appointment);
        assertThat(lastSummary).contains("\"paymentConfirmationCleared\": true")
                .contains("\"trustedContactConfirmationCleared\": true")
                .doesNotContain("Sami").doesNotContain("+216");
        assertThat(events.stream(AppointmentChecklistChanged.class)).hasSize(4);
    }

    @Test
    void nothingToConfirmIsAConflictAndRevokingClearsTheConfirmation() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/payment-confirmation", a.appointment).with(as(reception)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVANCE_AMOUNT_MISSING"));
        mvc.perform(post("/api/v1/appointments/{id}/trusted-contact-confirmation", a.appointment).with(as(reception)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRUSTED_CONTACT_MISSING"));

        adminDetails(reception, a.appointment, FULL_DETAILS);
        mvc.perform(post("/api/v1/appointments/{id}/payment-confirmation", a.appointment).with(as(reception)));
        mvc.perform(delete("/api/v1/appointments/{id}/payment-confirmation", a.appointment).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminDetails.advance.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.advance.paymentConfirmation").doesNotExist());
    }

    @Test
    void uploadingPaymentEvidenceDoesNotConfirmThePayment() throws Exception {
        adminDetails(reception, a.appointment, FULL_DETAILS);

        mvc.perform(multipart("/api/v1/patients/{id}/documents", a.patient)
                        .file(new MockMultipartFile("file", "recu-paiement.pdf", "application/pdf",
                                "%PDF-1.4\n% synthetic payment receipt\n".getBytes()))
                        .param("category", "OTHER")
                        .with(as(reception)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/appointments/{id}", a.appointment).with(as(reception)))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("PROVIDED"))
                .andExpect(jsonPath("$.adminDetails.advance.paymentConfirmation").doesNotExist());
    }

    @Test
    void adminDetailsAreValidated() throws Exception {
        for (String invalid : List.of(
                "{\"trustedContact\":{\"name\":\"X\",\"method\":\"PHONE\",\"value\":\"12\"}}",
                "{\"trustedContact\":{\"name\":\"X\",\"method\":\"EMAIL\",\"value\":\"not-an-email\"}}",
                "{\"trustedContact\":{\"name\":\"\",\"method\":\"PHONE\",\"value\":\"+216 20 000 000\"}}",
                "{\"trustedContact\":{\"name\":\"X\",\"method\":\"FAX\",\"value\":\"+216 20 000 000\"}}",
                "{\"expectedExaminations\":[\" \"]}",
                "{\"advanceAmount\":-1}",
                "{\"advanceAmount\":1.2345}")) {
            adminDetails(reception, a.appointment, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("audit_event WHERE entity_id = '" + a.appointment + "'")).isZero();
    }

    @Test
    void onlyStaffWithWritePermissionChangeAdminDetailsInTheirClinic() throws Exception {
        adminDetails(doctor, a.appointment, FULL_DETAILS).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/appointments/{id}/payment-confirmation", a.appointment).with(as(doctor)))
                .andExpect(status().isForbidden());
        adminDetails(manager, b.appointment, FULL_DETAILS).andExpect(status().isNotFound());
        // A practitioner still reads the checklist data of their own appointment.
        adminDetails(manager, a.appointment, FULL_DETAILS).andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/{id}", a.appointment).with(as(doctor)))
                .andExpect(jsonPath("$.adminDetails.advance.status").value("PROVIDED"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private ResultActions perform(MockHttpServletRequestBuilder request, User as, String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json).with(as(as)));
    }

    private ResultActions adminDetails(User as, UUID appointment, String json) throws Exception {
        return perform(put("/api/v1/appointments/{id}/admin-details", appointment), as, json);
    }

    private MockHttpServletRequestBuilder list(User as) {
        return get("/api/v1/appointments").with(as(as));
    }

    private static String newAppointment(Tenant t, String startsAt) {
        return """
                {"patientId":"%s","practitionerId":"%s","appointmentTypeId":"%s","startsAt":"%s"}"""
                .formatted(t.patient, t.practitioner, t.type, startsAt);
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(j -> {
            j.subject(user.id.toString());
            j.claim("clinicId", user.clinic.toString());
            j.claim("role", user.role.name());
            if (user.practitioner != null) {
                j.claim("practitionerId", user.practitioner.toString());
            }
        });
    }

    private record Tenant(UUID clinic, UUID practitioner, UUID type, UUID patient, UUID appointment) {
    }

    private record User(UUID id, UUID clinic, Role role, UUID practitioner) {
    }

    private Tenant tenant() {
        UUID clinic = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", clinic);
        UUID practitioner = practitioner(clinic);
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'CONSULT', 'Consultation')",
                type, clinic);
        UUID patient = patient(clinic, "P-A");
        Tenant t = new Tenant(clinic, practitioner, type, patient, null);
        UUID appointment = appointment(t, patient, practitioner, DAY.plusSeconds(8 * 3600), "SCHEDULED");
        return new Tenant(clinic, practitioner, type, patient, appointment);
    }

    private User user(Tenant tenant, Role role, UUID practitioner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, practitioner_id)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?, ?)""",
                id, tenant.clinic, id + "@appointments-test.test", role.name(), practitioner);
        return new User(id, tenant.clinic, role, practitioner);
    }

    private UUID practitioner(Tenant tenant) {
        return practitioner(tenant.clinic);
    }

    private UUID practitioner(UUID clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", id, clinic);
        return id;
    }

    private UUID patient(Tenant tenant, String ref) {
        return patient(tenant.clinic, ref);
    }

    private UUID patient(UUID clinic, String ref) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                id, clinic, ref);
        return id;
    }

    private UUID appointment(Tenant t, UUID patient, UUID practitioner, Instant startsAt, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                id, t.clinic, patient, practitioner, t.type, Timestamp.from(startsAt), status);
        return id;
    }

    private int count(String tableAndFilter) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Integer.class);
    }
}
