package com.clinicready;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The Flyway schema enforces tenant ownership on every tenant-owned table. */
@RepositoryTest
class TenantSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void coreTablesExist() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN
                  ('clinic', 'app_user', 'practitioner', 'patient', 'appointment_type', 'appointment')
                """, Integer.class);

        assertThat(count).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"app_user", "practitioner", "patient", "appointment_type", "appointment", "document",
            "document_text", "document_metadata", "requirement_rule", "audit_event"})
    void tenantOwnedTableHasRequiredClinicIdWithForeignKeyAndIndex(String table) {
        String nullable = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = 'clinic_id'
                """, String.class, table);
        Integer foreignKeys = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint c
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                WHERE c.contype = 'f' AND c.conrelid = ?::regclass
                  AND c.confrelid = 'clinic'::regclass AND a.attname = 'clinic_id'
                """, Integer.class, table);
        Integer leadingIndexes = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                WHERE i.indrelid = ?::regclass AND a.attname = 'clinic_id'
                """, Integer.class, table);

        assertThat(nullable).isEqualTo("NO");
        assertThat(foreignKeys).isEqualTo(1);
        assertThat(leadingIndexes).isPositive();
    }

    @Test
    void userCannotBeLinkedToPractitionerOfAnotherClinic() {
        UUID clinicA = insertClinic();
        UUID clinicB = insertClinic();
        UUID practitionerOfB = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr B')",
                practitionerOfB, clinicB);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, practitioner_id)
                VALUES (?, ?, 'cross@schema.test', 'x', 'Cross', 'PRACTITIONER', ?)
                """, UUID.randomUUID(), clinicA, practitionerOfB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertClinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Schema Test Clinic')", id);
        return id;
    }
}
