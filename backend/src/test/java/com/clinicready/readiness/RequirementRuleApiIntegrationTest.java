package com.clinicready.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/** Requirement rule configuration through the HTTP layer: permissions, validation, scoping, audit. */
@IntegrationTest
@Transactional
class RequirementRuleApiIntegrationTest {

    private static final String CONSENT_RULE = """
            {"documentCategory":"CONSENT_FORM","mandatory":true,"validityDays":30,"needsVerification":true}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Requests join the test transaction, so JPA changes are flushed by hand before reading with JDBC. */
    @PersistenceContext
    private EntityManager entityManager;

    // ---- read ------------------------------------------------------------------------------------

    @Test
    void everyRoleListsAppointmentTypesAndTheirRules() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);
        UUID type = appointmentType(a, "MRI");
        create(manager, type, CONSENT_RULE).andExpect(status().isCreated());
        create(manager, type, """
                {"documentCategory":"ID_DOCUMENT","mandatory":false,"validityDays":null,"needsVerification":false}""")
                .andExpect(status().isCreated());

        for (Role role : Role.values()) {
            User u = user(a, role);
            mvc.perform(get("/api/v1/appointment-types").with(as(u)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(type.toString()))
                    .andExpect(jsonPath("$[0].code").value("MRI"));
            mvc.perform(get("/api/v1/appointment-types/{id}/requirements", type).with(as(u)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].documentCategory").value(contains("CONSENT_FORM", "ID_DOCUMENT")))
                    .andExpect(jsonPath("$[0].appointmentTypeId").value(type.toString()))
                    .andExpect(jsonPath("$[0].mandatory").value(true))
                    .andExpect(jsonPath("$[0].validityDays").value(30))
                    .andExpect(jsonPath("$[0].needsVerification").value(true))
                    .andExpect(jsonPath("$[1].mandatory").value(false))
                    .andExpect(jsonPath("$[1].validityDays").doesNotExist());
        }
    }

    // ---- write -----------------------------------------------------------------------------------

    @Test
    void managerCreatesUpdatesAndDeletesARuleAndEachChangeIsAudited() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);
        UUID type = appointmentType(a, "CONSULT");

        String body = create(manager, type, CONSENT_RULE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentCategory").value("CONSENT_FORM"))
                .andReturn().getResponse().getContentAsString();
        UUID rule = UUID.fromString(JsonPath.read(body, "$.id"));

        update(manager, rule, """
                {"documentCategory":"CONSENT_FORM","mandatory":false,"validityDays":90,"needsVerification":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rule.toString()))
                .andExpect(jsonPath("$.mandatory").value(false))
                .andExpect(jsonPath("$.validityDays").value(90));

        mvc.perform(delete("/api/v1/requirements/{id}", rule).with(as(manager)))
                .andExpect(status().isNoContent());
        assertThat(count("requirement_rule")).isZero();

        entityManager.flush();
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT action, actor_user_id, clinic_id, entity_type,
                       change_summary->'before'->>'mandatory' AS before_mandatory,
                       change_summary->'after'->>'mandatory' AS after_mandatory,
                       change_summary->>'appointmentTypeId' AS type_id
                FROM audit_event WHERE entity_id = ? ORDER BY occurred_at""", rule);
        assertThat(events).extracting(e -> e.get("action"))
                .containsExactly("REQUIREMENT_RULE_CREATED", "REQUIREMENT_RULE_UPDATED", "REQUIREMENT_RULE_DELETED");
        assertThat(events).allSatisfy(e -> {
            assertThat(e.get("actor_user_id")).isEqualTo(manager.id);
            assertThat(e.get("clinic_id")).isEqualTo(a.id);
            assertThat(e.get("entity_type")).isEqualTo("REQUIREMENT_RULE");
            assertThat(e.get("type_id")).isEqualTo(type.toString());
        });
        assertThat(events.get(1).get("before_mandatory")).isEqualTo("true");
        assertThat(events.get(1).get("after_mandatory")).isEqualTo("false");
        assertThat(events.get(2).get("after_mandatory")).isNull();
    }

    @Test
    void anUpdateThatChangesNothingIsNotAudited() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);
        UUID rule = createdRule(manager, appointmentType(a, "CONSULT"));

        update(manager, rule, CONSENT_RULE).andExpect(status().isOk());

        assertThat(count("audit_event WHERE entity_id = '" + rule + "'")).isEqualTo(1);
    }

    @Test
    void receptionistsAndPractitionersCannotChangeRules() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);
        UUID type = appointmentType(a, "CONSULT");
        UUID rule = createdRule(manager, type);

        for (Role role : List.of(Role.RECEPTIONIST, Role.PRACTITIONER)) {
            User u = user(a, role);
            create(u, type, CONSENT_RULE.replace("CONSENT_FORM", "LAB_RESULT"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            update(u, rule, CONSENT_RULE.replace("30", "60")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/requirements/{id}", rule).with(as(u))).andExpect(status().isForbidden());
        }
        assertThat(count("requirement_rule")).isEqualTo(1);
        assertThat(count("audit_event WHERE entity_id = '" + rule + "'")).isEqualTo(1);
    }

    @Test
    void anotherClinicsTypesAndRulesAreNotFound() throws Exception {
        Clinic a = clinic();
        Clinic b = clinic();
        UUID typeOfB = appointmentType(b, "CONSULT");
        UUID ruleOfB = createdRule(user(b, Role.CLINIC_MANAGER), typeOfB);
        User managerOfA = user(a, Role.CLINIC_MANAGER);

        mvc.perform(get("/api/v1/appointment-types").with(as(managerOfA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/appointment-types/{id}/requirements", typeOfB).with(as(managerOfA)))
                .andExpect(status().isNotFound());
        create(managerOfA, typeOfB, CONSENT_RULE).andExpect(status().isNotFound());
        update(managerOfA, ruleOfB, CONSENT_RULE.replace("30", "60"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(delete("/api/v1/requirements/{id}", ruleOfB).with(as(managerOfA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTypeHasAtMostOneRulePerCategory() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);
        UUID type = appointmentType(a, "CONSULT");
        createdRule(manager, type);
        UUID labRule = UUID.fromString(JsonPath.read(create(manager, type,
                CONSENT_RULE.replace("CONSENT_FORM", "LAB_RESULT")).andReturn().getResponse().getContentAsString(),
                "$.id"));

        create(manager, type, CONSENT_RULE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_CONFLICT"));
        update(manager, labRule, CONSENT_RULE).andExpect(status().isConflict());

        // The same category on another type is fine.
        create(manager, appointmentType(a, "MRI"), CONSENT_RULE).andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidRules() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);
        UUID type = appointmentType(a, "CONSULT");

        for (String invalid : List.of(
                "{\"mandatory\":true,\"needsVerification\":false}",
                CONSENT_RULE.replace("CONSENT_FORM", "PASSPORT"),
                CONSENT_RULE.replace("\"validityDays\":30", "\"validityDays\":0"),
                CONSENT_RULE.replace("\"validityDays\":30", "\"validityDays\":3651"),
                CONSENT_RULE.replace("\"mandatory\":true,", ""))) {
            create(manager, type, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("requirement_rule")).isZero();
    }

    @Test
    void createReturnsTheRuleLocation() throws Exception {
        Clinic a = clinic();
        User manager = user(a, Role.CLINIC_MANAGER);

        String body = create(manager, appointmentType(a, "CONSULT"), CONSENT_RULE)
                .andReturn().getResponse().getContentAsString();

        create(manager, appointmentType(a, "MRI"), CONSENT_RULE)
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/requirements/")));
        assertThat((String) JsonPath.read(body, "$.updatedAt")).isNotBlank();
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private ResultActions create(User as, UUID type, String json) throws Exception {
        return mvc.perform(post("/api/v1/appointment-types/{id}/requirements", type)
                .contentType(MediaType.APPLICATION_JSON).content(json).with(as(as)));
    }

    private ResultActions update(User as, UUID rule, String json) throws Exception {
        return mvc.perform(put("/api/v1/requirements/{id}", rule)
                .contentType(MediaType.APPLICATION_JSON).content(json).with(as(as)));
    }

    private UUID createdRule(User manager, UUID type) throws Exception {
        String body = create(manager, type, CONSENT_RULE).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(j -> {
            j.subject(user.id.toString());
            j.claim("clinicId", user.clinic.id.toString());
            j.claim("role", user.role.name());
        });
    }

    private record Clinic(UUID id) {
    }

    private record User(UUID id, Clinic clinic, Role role) {
    }

    private Clinic clinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", id);
        return new Clinic(id);
    }

    private User user(Clinic clinic, Role role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?)""",
                id, clinic.id, id + "@rules-test.test", role.name());
        return new User(id, clinic, role);
    }

    private UUID appointmentType(Clinic clinic, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, ?, 'Test')",
                id, clinic.id, code);
        return id;
    }

    private int count(String tableAndFilter) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Integer.class);
    }
}
