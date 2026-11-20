package com.clinicready.seed;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes {@link DemoDataGenerator} output with upserts keyed on the deterministic ids, so running it
 * any number of times leaves exactly one copy of each row (idempotent) and re-anchors the
 * appointments to the current day. Active only under the {@code demo} profile.
 *
 * <p>Uses plain JDBC on purpose: seeding must keep working when entities change shape. Rows are
 * inserted in dependency order to satisfy the composite (id, clinic_id) foreign keys.
 *
 * <p>Never logs names or other patient details, only row counts.
 */
@Service
@Profile("demo")
public class DemoDataSeeder {

    /**
     * BCrypt hash of the documented demo password {@code password}. Demo profile only; never run the
     * demo profile against real data.
     */
    static final String DEMO_PASSWORD_HASH =
            "$2y$10$VZGczp9dfCRqdr.f2HOcEOXF9addK2mJc/shVOM/b57lpZJuxehoi";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    @FunctionalInterface
    private interface Binder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }

    private final JdbcTemplate jdbc;

    public DemoDataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void seed(LocalDate today) {
        DemoData data = DemoDataGenerator.generate(today);
        UUID clinicId = data.clinic().id();

        jdbc.update("""
                INSERT INTO clinic (id, name, city) VALUES (?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, city = EXCLUDED.city""",
                clinicId, data.clinic().name(), data.clinic().city());

        batch("""
                INSERT INTO practitioner (id, clinic_id, full_name, specialty, active)
                VALUES (?, ?, ?, ?, TRUE)
                ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name,
                    specialty = EXCLUDED.specialty, active = TRUE""",
                data.practitioners(), (ps, p) -> {
                    ps.setObject(1, p.id());
                    ps.setObject(2, clinicId);
                    ps.setString(3, p.fullName());
                    ps.setString(4, p.specialty());
                });

        batch("""
                INSERT INTO app_user
                    (id, clinic_id, email, password_hash, full_name, role, enabled, practitioner_id)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)
                ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email,
                    password_hash = EXCLUDED.password_hash, full_name = EXCLUDED.full_name,
                    role = EXCLUDED.role, enabled = TRUE, practitioner_id = EXCLUDED.practitioner_id""",
                data.users(), (ps, u) -> {
                    ps.setObject(1, u.id());
                    ps.setObject(2, clinicId);
                    ps.setString(3, u.email());
                    ps.setString(4, DEMO_PASSWORD_HASH);
                    ps.setString(5, u.fullName());
                    ps.setString(6, u.role());
                    ps.setObject(7, u.practitionerId());
                });

        batch("""
                INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code, label = EXCLUDED.label""",
                data.appointmentTypes(), (ps, t) -> {
                    ps.setObject(1, t.id());
                    ps.setObject(2, clinicId);
                    ps.setString(3, t.code());
                    ps.setString(4, t.label());
                });

        batch("""
                INSERT INTO patient (id, clinic_id, patient_ref, full_name, phone, preferred_language)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET patient_ref = EXCLUDED.patient_ref,
                    full_name = EXCLUDED.full_name, phone = EXCLUDED.phone,
                    preferred_language = EXCLUDED.preferred_language""",
                data.patients(), (ps, p) -> {
                    ps.setObject(1, p.id());
                    ps.setObject(2, clinicId);
                    ps.setString(3, p.ref());
                    ps.setString(4, p.fullName());
                    ps.setString(5, p.phone());
                    ps.setString(6, p.language());
                });

        batch("""
                INSERT INTO appointment
                    (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET patient_id = EXCLUDED.patient_id,
                    practitioner_id = EXCLUDED.practitioner_id,
                    appointment_type_id = EXCLUDED.appointment_type_id,
                    starts_at = EXCLUDED.starts_at, status = EXCLUDED.status""",
                data.appointments(), (ps, a) -> {
                    ps.setObject(1, a.id());
                    ps.setObject(2, clinicId);
                    ps.setObject(3, a.patientId());
                    ps.setObject(4, a.practitionerId());
                    ps.setObject(5, a.appointmentTypeId());
                    ps.setTimestamp(6, Timestamp.from(a.startsAt().toInstant()));
                    ps.setString(7, a.status());
                });

        seedRequirementRules(data, clinicId);
        int detailed = seedAdminDetails(data, clinicId);

        log.info("Demo seed applied: {} practitioners, {} users, {} appointment types, "
                        + "{} patients, {} appointments",
                data.practitioners().size(), data.users().size(), data.appointmentTypes().size(),
                data.patients().size(), data.appointments().size());
        log.info("Demo rules: {} requirement rules, {} appointments given examinations and an advance",
                DemoClinicConfig.RULES.size(), detailed);
    }

    private record RuleRow(UUID typeId, DemoClinicConfig.Rule rule) {}

    /** The documents each appointment type needs. Updated in place on every start. */
    private void seedRequirementRules(DemoData data, UUID clinicId) {
        Map<String, UUID> typeIds = data.appointmentTypes().stream()
                .collect(Collectors.toMap(DemoData.AppointmentType::code, DemoData.AppointmentType::id));
        List<RuleRow> rows = new ArrayList<>();
        for (DemoClinicConfig.Rule rule : DemoClinicConfig.RULES) {
            UUID typeId = typeIds.get(rule.typeCode());
            if (typeId != null) {
                rows.add(new RuleRow(typeId, rule));
            }
        }
        batch("""
                INSERT INTO requirement_rule
                    (id, clinic_id, appointment_type_id, document_category, mandatory, validity_days,
                     needs_verification)
                VALUES (?, ?, ?, ?, ?, ?, FALSE)
                ON CONFLICT (id) DO UPDATE SET mandatory = EXCLUDED.mandatory,
                    validity_days = EXCLUDED.validity_days, updated_at = now()""",
                rows, (ps, r) -> {
                    ps.setObject(1, r.rule().id());
                    ps.setObject(2, clinicId);
                    ps.setObject(3, r.typeId());
                    ps.setString(4, r.rule().category());
                    ps.setBoolean(5, r.rule().mandatory());
                    ps.setObject(6, r.rule().validityDays());
                });
    }

    /**
     * Expected examinations and an advance for the upcoming appointments, as staff would have entered them;
     * about half the advances are confirmed and one appointment in seven is left blank (readiness then asks
     * for it). Only fills appointments staff have not touched, so edits made in the demo survive a restart.
     */
    private int seedAdminDetails(DemoData data, UUID clinicId) {
        UUID reception = data.users().stream().filter(u -> u.role().equals("RECEPTIONIST")).map(DemoData.User::id)
                .findFirst().orElseThrow();
        Map<UUID, String> typeCodes = data.appointmentTypes().stream()
                .collect(Collectors.toMap(DemoData.AppointmentType::id, DemoData.AppointmentType::code));
        int updated = 0;
        int index = 0;
        for (DemoData.Appointment a : data.appointments()) {
            if (!a.status().equals("SCHEDULED")) {
                continue;
            }
            int i = index++;
            if (i % 7 == 6) {
                continue;
            }
            String code = typeCodes.get(a.appointmentTypeId());
            String examinations = DemoClinicConfig.examinationsFor(code).stream()
                    .map(e -> "\"" + e + "\"").collect(Collectors.joining(",", "[", "]"));
            var advance = DemoClinicConfig.advanceFor(code);
            boolean confirmed = advance.signum() > 0 && i % 2 == 0;
            updated += jdbc.update("""
                    UPDATE appointment SET expected_examinations = ?::jsonb, advance_amount = ?,
                        payment_confirmed_by = ?, payment_confirmed_at = ?, updated_at = now()
                    WHERE id = ? AND clinic_id = ? AND expected_examinations IS NULL AND advance_amount IS NULL""",
                    examinations, advance, confirmed ? reception : null,
                    confirmed ? Timestamp.from(Instant.now()) : null, a.id(), clinicId);
        }
        return updated;
    }

    private <T> void batch(String sql, List<T> rows, Binder<T> binder) {
        jdbc.batchUpdate(sql, rows, rows.size(), binder::bind);
    }
}
