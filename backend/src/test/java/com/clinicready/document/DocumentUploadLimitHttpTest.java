package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.clinicready.IntegrationTest;

/**
 * Real HTTP (MockMvc skips Spring's multipart parser, so it cannot exercise the size limit set in
 * application.yml). Commits its own rows, so it cleans them up.
 */
@IntegrationTest
@TestPropertySource(properties = "clinicready.documents.max-size=20KB")
class DocumentUploadLimitHttpTest {

    private final UUID clinicId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder encoder;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Limit Test')", clinicId);
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', 'RECEPTIONIST')""",
                userId, clinicId, userId + "@limit-test.test");
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, 'L-0001', 'Patient Test')",
                patientId, clinicId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM document WHERE clinic_id = ?", clinicId);
        jdbc.update("DELETE FROM patient WHERE clinic_id = ?", clinicId);
        jdbc.update("DELETE FROM app_user WHERE clinic_id = ?", clinicId);
        jdbc.update("DELETE FROM clinic WHERE id = ?", clinicId);
    }

    @Test
    void springRejectsAnOversizedMultipartBodyWithAJson413() {
        byte[] big = new byte[30 * 1024];
        System.arraycopy("%PDF-1.4\n".getBytes(), 0, big, 0, 9);

        ResponseEntity<String> response = post(big);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("\"code\":\"FILE_TOO_LARGE\"");
    }

    @Test
    void aSmallFileOverRealHttpIsAccepted() {
        ResponseEntity<String> response = post("%PDF-1.4\nsmall\n".getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document WHERE clinic_id = ?", Integer.class, clinicId))
                .isEqualTo(1);
    }

    private ResponseEntity<String> post(byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "scan.pdf";
            }
        });
        body.add("category", "OTHER");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token());
        return http.postForEntity("/api/v1/patients/{id}/documents", new HttpEntity<>(body, headers), String.class,
                patientId);
    }

    private String token() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("clinicready")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("clinicId", clinicId.toString())
                .claim("role", "RECEPTIONIST")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
