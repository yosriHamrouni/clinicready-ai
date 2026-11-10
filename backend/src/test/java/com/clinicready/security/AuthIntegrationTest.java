package com.clinicready.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.clinicready.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class AuthIntegrationTest {

    private static final UUID CLINIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DISABLED_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String EMAIL = "reception@auth-test.test";
    private static final String DISABLED_EMAIL = "disabled@auth-test.test";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedUsers() {
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Auth Test Clinic') ON CONFLICT DO NOTHING", CLINIC_ID);
        insertUser(USER_ID, EMAIL, "RECEPTIONIST", true);
        insertUser(DISABLED_USER_ID, DISABLED_EMAIL, "PRACTITIONER", false);
    }

    private void insertUser(UUID id, String email, String role, boolean enabled) {
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, enabled)
                VALUES (?, ?, ?, ?, 'Test User', ?, ?) ON CONFLICT DO NOTHING
                """, id, CLINIC_ID, email, passwordEncoder.encode(PASSWORD), role, enabled);
    }

    @Test
    void loginReturnsTokenUserAndHttpOnlyCookie() throws Exception {
        mvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(8 * 3600))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.clinicId").value(CLINIC_ID.toString()))
                .andExpect(jsonPath("$.user.roles[0]").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(cookie().httpOnly(AuthController.ACCESS_TOKEN_COOKIE, true))
                .andExpect(cookie().secure(AuthController.ACCESS_TOKEN_COOKIE, true))
                .andExpect(cookie().sameSite(AuthController.ACCESS_TOKEN_COOKIE, "Strict"))
                .andExpect(cookie().path(AuthController.ACCESS_TOKEN_COOKIE, "/api"));
    }

    @Test
    void loginEmailIsCaseInsensitive() throws Exception {
        mvc.perform(login(EMAIL.toUpperCase(), PASSWORD)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({
            "reception@auth-test.test, wrong-password",
            "nobody@auth-test.test, correct horse battery",
            "disabled@auth-test.test, correct horse battery"
    })
    void loginFailuresAreIndistinguishable(String email, String password) throws Exception {
        mvc.perform(login(email, password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(cookie().doesNotExist(AuthController.ACCESS_TOKEN_COOKIE));
    }

    @Test
    void loginRejectsInvalidBody() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void loginIgnoresStaleCookie() throws Exception {
        mvc.perform(login(EMAIL, PASSWORD).cookie(new Cookie(AuthController.ACCESS_TOKEN_COOKIE, "garbage")))
                .andExpect(status().isOk());
    }

    @Test
    void meWithBearerTokenReturnsCurrentUser() throws Exception {
        String token = loginAndGetToken();

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.clinicId").value(CLINIC_ID.toString()))
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST"));
    }

    @Test
    void meWithCookieReturnsCurrentUser() throws Exception {
        MvcResult result = mvc.perform(login(EMAIL, PASSWORD)).andReturn();
        Cookie cookie = result.getResponse().getCookie(AuthController.ACCESS_TOKEN_COOKIE);

        mvc.perform(get("/api/v1/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void meWithoutTokenIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meRejectsTamperedToken() throws Exception {
        String token = loginAndGetToken();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meRejectsExpiredToken() throws Exception {
        String expired = signToken(IntegrationTest.JWT_SECRET, Instant.now().minusSeconds(3600));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsTokenSignedWithAnotherKey() throws Exception {
        String forged = signToken("another-secret-that-is-also-32-bytes-long!!", Instant.now().plusSeconds(3600));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsTokenOfDisabledUser() throws Exception {
        String token = signTokenFor(DISABLED_USER_ID, IntegrationTest.JWT_SECRET, Instant.now().plusSeconds(3600));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/patients",
            "POST, /api/v1/appointments",
            "GET, /api/v1/does-not-exist",
            "GET, /actuator/info",
            "GET, /actuator/env"
    })
    void everyOtherEndpointRejectsUnauthenticatedRequests(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutClearsCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.ACCESS_TOKEN_COOKIE, 0));
    }

    @Test
    void passwordsAreStoredWithBcrypt() {
        String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, USER_ID);

        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String loginAndGetToken() throws Exception {
        MvcResult result = mvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String signToken(String secret, Instant expiresAt) {
        return signTokenFor(USER_ID, secret, expiresAt);
    }

    private static String signTokenFor(UUID userId, String secret, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("clinicready")
                .subject(userId.toString())
                .issuedAt(expiresAt.minusSeconds(7200))
                .expiresAt(expiresAt)
                .claim("clinicId", CLINIC_ID.toString())
                .claim("role", "RECEPTIONIST")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
