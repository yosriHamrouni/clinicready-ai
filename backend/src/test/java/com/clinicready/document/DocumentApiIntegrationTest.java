package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;

/** Upload, storage, checksum, limits and authorization of patient documents, through the HTTP layer. */
@IntegrationTest
@Transactional
@TestPropertySource(properties = "clinicready.documents.max-size=20KB")
class DocumentApiIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static final byte[] PDF = ("%PDF-1.4\n% synthetic test document\n").getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("clinicready.documents.storage-path", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("clinicready-docs-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ---- upload ---------------------------------------------------------------------------------

    @Test
    void receptionistUploadsAPdfAndItIsStoredOutsideTheDatabaseWithItsChecksum() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);

        String body = upload(reception, patient, "ordonnance.pdf", "application/pdf", PDF, "PRESCRIPTION", "SCAN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patient.toString()))
                .andExpect(jsonPath("$.category").value("PRESCRIPTION"))
                .andExpect(jsonPath("$.source").value("SCAN"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.sha256").value(sha256(PDF)))
                .andExpect(jsonPath("$.uploadedBy").value(reception.id.toString()))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID documentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
        Path file = STORAGE_ROOT.resolve(a.id + "/" + documentId);
        assertThat(file).exists();
        assertThat(Files.readAllBytes(file)).isEqualTo(PDF);
    }

    @Test
    void sourceDefaultsToStaffUploadAndPngAndJpegAreAccepted() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);

        upload(reception, patient, "cin.png", "image/png", PNG, "ID_DOCUMENT", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("STAFF_UPLOAD"))
                .andExpect(jsonPath("$.contentType").value("image/png"));
        upload(reception, patient, "carte.jpg", "image/jpeg", JPEG, "INSURANCE_CARD", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    void eachUploadOfTheSameCategoryIsANewVersion() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);

        upload(reception, patient, "a.pdf", "application/pdf", PDF, "LAB_RESULT", null)
                .andExpect(jsonPath("$.version").value(1));
        upload(reception, patient, "b.pdf", "application/pdf", PDF, "LAB_RESULT", null)
                .andExpect(jsonPath("$.version").value(2));
        upload(reception, patient, "c.pdf", "application/pdf", PDF, "IMAGING_REPORT", null)
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/v1/patients/{id}/documents", patient).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.category=='LAB_RESULT')].version").value(org.hamcrest.Matchers.contains(2, 1)));
    }

    // ---- type and size limits ----------------------------------------------------------------

    @Test
    void rejectsFilesThatAreNotPdfJpegOrPngEvenIfNamedOrDeclaredAsOne() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};

        upload(reception, patient, "virus.pdf", "application/pdf", exe, "OTHER", null)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(reception, patient, "page.html", "text/html", "<html></html>".getBytes(), "OTHER", null)
                .andExpect(status().isUnsupportedMediaType());
        // Real PNG bytes, but declared as a PDF.
        upload(reception, patient, "x.pdf", "application/pdf", PNG, "OTHER", null)
                .andExpect(status().isUnsupportedMediaType());

        assertThat(count("document")).isZero();
    }

    @Test
    void rejectsEmptyFilesAndOversizedFiles() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);
        byte[] tooBig = new byte[21 * 1024];
        System.arraycopy(PDF, 0, tooBig, 0, PDF.length);

        upload(reception, patient, "empty.pdf", "application/pdf", new byte[0], "OTHER", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        upload(reception, patient, "big.pdf", "application/pdf", tooBig, "OTHER", null)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        assertThat(count("document")).isZero();
    }

    @Test
    void rejectsMissingFileAndUnknownCategory() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);

        mvc.perform(multipart("/api/v1/patients/{id}/documents", patient).param("category", "OTHER")
                        .with(as(reception)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        upload(reception, patient, "a.pdf", "application/pdf", PDF, "NOT_A_CATEGORY", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- authorized serving ------------------------------------------------------------------

    @Test
    void contentIsServedWithTheOriginalBytesAndSafeHeaders() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);
        UUID doc = uploadedId(reception, patient, PDF);

        mvc.perform(get("/api/v1/documents/{id}/content", doc).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("ETag", "\"" + sha256(PDF) + "\""))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/v1/documents/{id}", doc).with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").value(sha256(PDF)));
    }

    @Test
    void anonymousRequestsAreRejectedEverywhere() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID patient = patient(a);
        UUID doc = uploadedId(reception, patient, PDF);

        mvc.perform(get("/api/v1/documents/{id}/content", doc)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/documents/{id}", doc)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/{id}/documents", patient)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/patients/{id}/documents", patient)
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                        .param("category", "OTHER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherClinicSeesNothing() throws Exception {
        Clinic a = clinic();
        Clinic b = clinic();
        User receptionA = user(a, Role.RECEPTIONIST, null);
        User managerB = user(b, Role.CLINIC_MANAGER, null);
        UUID patientA = patient(a);
        UUID doc = uploadedId(receptionA, patientA, PDF);

        mvc.perform(get("/api/v1/documents/{id}", doc).with(as(managerB))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/{id}/content", doc).with(as(managerB))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/patients/{id}/documents", patientA).with(as(managerB)))
                .andExpect(status().isNotFound());
        upload(managerB, patientA, "a.pdf", "application/pdf", PDF, "OTHER", null).andExpect(status().isNotFound());
        assertThat(count("document")).isEqualTo(1);
    }

    @Test
    void practitionerReadsOnlyDocumentsOfTheirOwnPatientsAndCannotUpload() throws Exception {
        Clinic a = clinic();
        User reception = user(a, Role.RECEPTIONIST, null);
        UUID practitionerRow = practitioner(a);
        User doctor = user(a, Role.PRACTITIONER, practitionerRow);
        UUID ownPatient = patient(a);
        UUID otherPatient = patient(a);
        appointment(a, ownPatient, practitionerRow);
        UUID ownDoc = uploadedId(reception, ownPatient, PDF);
        UUID otherDoc = uploadedId(reception, otherPatient, PDF);

        mvc.perform(get("/api/v1/patients/{id}/documents", ownPatient).with(as(doctor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/documents/{id}/content", ownDoc).with(as(doctor)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));

        mvc.perform(get("/api/v1/patients/{id}/documents", otherPatient).with(as(doctor)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/{id}", otherDoc).with(as(doctor))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/{id}/content", otherDoc).with(as(doctor)))
                .andExpect(status().isNotFound());

        upload(doctor, ownPatient, "a.pdf", "application/pdf", PDF, "OTHER", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private ResultActions upload(User as, UUID patient, String filename, String contentType, byte[] bytes,
            String category, String source) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/patients/{id}/documents", patient);
        request.file(new MockMultipartFile("file", filename, contentType, bytes)).param("category", category);
        if (source != null) {
            request.param("source", source);
        }
        return mvc.perform(request.with(as(as)));
    }

    private UUID uploadedId(User as, UUID patient, byte[] bytes) throws Exception {
        String body = upload(as, patient, "doc.pdf", "application/pdf", bytes, "OTHER", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
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
                id, clinic.id, id + "@documents-test.test", role.name(), practitionerId);
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

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
