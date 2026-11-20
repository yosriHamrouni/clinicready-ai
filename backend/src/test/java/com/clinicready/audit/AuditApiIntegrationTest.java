package com.clinicready.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;

/** GET /audit-events: who may read what, filters, paging, and clinic isolation. */
@IntegrationTest
@Transactional
class AuditApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private record User(UUID id, UUID clinic, Role role) {
    }

    @Test
    void managerSeesEveryEventOfTheClinicNewestFirstWithActorNames() throws Exception {
        UUID clinic = clinic();
        User manager = user(clinic, Role.CLINIC_MANAGER);
        User reception = user(clinic, Role.RECEPTIONIST);
        UUID run = UUID.randomUUID();
        event(clinic, reception.id, "PATIENT_CREATED", "PATIENT", null, 3);
        event(clinic, manager.id, "PAYMENT_CONFIRMED", "APPOINTMENT", null, 2);
        event(clinic, null, "AGENT_RUN_STARTED", "AGENT_RUN", run, 1);

        mvc.perform(get("/api/v1/audit-events").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].action").value("AGENT_RUN_STARTED"))
                .andExpect(jsonPath("$.items[0].actorName").doesNotExist())
                .andExpect(jsonPath("$.items[1].action").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.items[1].actorName").value("User Test"))
                .andExpect(jsonPath("$.items[2].action").value("PATIENT_CREATED"));
    }

    @Test
    void receptionistSeesOnlyTheirOwnActions() throws Exception {
        UUID clinic = clinic();
        User manager = user(clinic, Role.CLINIC_MANAGER);
        User reception = user(clinic, Role.RECEPTIONIST);
        User colleague = user(clinic, Role.RECEPTIONIST);
        event(clinic, reception.id, "PATIENT_CREATED", "PATIENT", null, 3);
        event(clinic, colleague.id, "PATIENT_CREATED", "PATIENT", null, 2);
        event(clinic, manager.id, "PAYMENT_CONFIRMED", "APPOINTMENT", null, 1);

        mvc.perform(get("/api/v1/audit-events").with(as(reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].actorUserId").value(reception.id.toString()));
    }

    @Test
    void practitionersAreForbiddenAndOtherClinicsSeeNothing() throws Exception {
        UUID clinic = clinic();
        User practitioner = user(clinic, Role.PRACTITIONER);
        User manager = user(clinic, Role.CLINIC_MANAGER);
        User stranger = user(clinic(), Role.CLINIC_MANAGER);
        event(clinic, manager.id, "PATIENT_CREATED", "PATIENT", null, 1);

        mvc.perform(get("/api/v1/audit-events").with(as(practitioner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-events").with(as(stranger)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtersByRunEntityAndPeriodAndPages() throws Exception {
        UUID clinic = clinic();
        User manager = user(clinic, Role.CLINIC_MANAGER);
        UUID run = UUID.randomUUID();
        event(clinic, manager.id, "AGENT_RUN_STARTED", "AGENT_RUN", run, 5);
        event(clinic, manager.id, "AGENT_TOOL_CALLED", "AGENT_RUN", run, 4);
        event(clinic, manager.id, "AGENT_RUN_FINISHED", "AGENT_RUN", run, 3);
        event(clinic, manager.id, "PATIENT_CREATED", "PATIENT", null, 2);

        mvc.perform(get("/api/v1/audit-events").param("runId", run.toString()).with(as(manager)))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v1/audit-events").param("entityType", "PATIENT").with(as(manager)))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/audit-events").param("from", Instant.now().minus(210, ChronoUnit.SECONDS).toString())
                        .with(as(manager)))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/audit-events").param("size", "2").param("page", "1").with(as(manager)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].action").value("AGENT_TOOL_CALLED"));
        mvc.perform(get("/api/v1/audit-events")
                        .param("from", Instant.now().toString())
                        .param("to", Instant.now().minus(1, ChronoUnit.DAYS).toString()).with(as(manager)))
                .andExpect(status().isBadRequest());
    }

    /** An event {@code minutesAgo} minutes old; {@code runId} null for a change outside any run. */
    private void event(UUID clinic, UUID actor, String action, String entityType, UUID runId, int minutesAgo) {
        jdbc.update("""
                INSERT INTO audit_event (id, clinic_id, actor_user_id, action, entity_type, entity_id, change_summary,
                                         run_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?)""", UUID.randomUUID(), clinic, actor, action, entityType,
                runId != null && entityType.equals("AGENT_RUN") ? runId : UUID.randomUUID(), runId,
                Timestamp.from(Instant.now().minus(minutesAgo * 60L, ChronoUnit.SECONDS)));
    }

    private UUID clinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", id);
        return id;
    }

    private User user(UUID clinic, Role role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?)""", id, clinic, id + "@audit-test.test",
                role.name());
        return new User(id, clinic, role);
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(j -> j.subject(user.id.toString()).claim("clinicId", user.clinic.toString())
                .claim("role", user.role.name()));
    }
}
