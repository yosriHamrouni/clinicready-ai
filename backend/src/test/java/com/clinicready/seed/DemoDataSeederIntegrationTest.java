package com.clinicready.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.clinicready.IntegrationTest;
import com.clinicready.document.DemoDocumentSeeder;

/** The runner already seeded on startup (demo profile); here we re-run it and compare. */
@IntegrationTest
@ActiveProfiles("demo")
class DemoDataSeederIntegrationTest {

    private static final List<String> TABLES =
            List.of("clinic", "practitioner", "app_user", "appointment_type", "patient", "appointment");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    DemoDocumentSeeder documentSeeder;

    private Map<String, Integer> counts() {
        return TABLES.stream().collect(java.util.stream.Collectors.toMap(t -> t,
                t -> jdbc.queryForObject("SELECT count(*) FROM " + t, Integer.class)));
    }

    @Test
    void seedsTheDemoClinicOnStartup() {
        var counts = counts();
        assertThat(counts).containsEntry("clinic", 1).containsEntry("practitioner", 2)
                .containsEntry("patient", 40).containsEntry("app_user", 4);
        assertThat(counts.get("appointment")).isGreaterThan(30);
        assertThat(jdbc.queryForList("SELECT DISTINCT role FROM app_user", String.class))
                .containsExactlyInAnyOrder("RECEPTIONIST", "PRACTITIONER", "CLINIC_MANAGER");
    }

    @Test
    void isIdempotent() {
        var before = counts();
        LocalDate today = LocalDate.now(DemoDataGenerator.CLINIC_ZONE);

        seeder.seed(today);
        seeder.seed(today);

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void reSeedingOnALaterDayMovesAppointmentsWithoutDuplicatingThem() {
        var before = counts();
        seeder.seed(LocalDate.now(DemoDataGenerator.CLINIC_ZONE).plusDays(7));
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void storesArabicNamesIntact() {
        List<String> arabicNames = jdbc.queryForList(
                "SELECT full_name FROM patient WHERE preferred_language = 'AR'", String.class);
        assertThat(arabicNames).hasSize(16).allSatisfy(n -> assertThat(n).matches(".*\\p{InArabic}.*"));
    }

    @Test
    void seedsRequirementRulesForEveryAppointmentType() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM requirement_rule", Integer.class))
                .isEqualTo(DemoClinicConfig.RULES.size());
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT appointment_type_id) FROM requirement_rule", Integer.class)).isEqualTo(6);
    }

    @Test
    void givesUpcomingAppointmentsExaminationsAndAnAdvanceButLeavesSomeBlank() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM appointment WHERE status = 'SCHEDULED' AND expected_examinations IS NOT NULL
                    AND advance_amount IS NOT NULL""", Integer.class)).isGreaterThan(10);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM appointment WHERE status = 'SCHEDULED' AND expected_examinations IS NULL""",
                Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment WHERE payment_confirmed_at IS NOT NULL",
                Integer.class)).isGreaterThan(0);
    }

    @Test
    void seedsReadableSyntheticDocumentsWithExtractionAndKeepsThemOnReSeed() {
        int documents = jdbc.queryForObject("SELECT count(*) FROM document", Integer.class);
        assertThat(documents).isGreaterThan(20);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM document d JOIN document_extraction e ON e.document_id = d.id
                    JOIN document_text t ON t.document_id = d.id""", Integer.class)).isEqualTo(documents);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM document_extraction WHERE review_reason = 'LOW_CONFIDENCE'", Integer.class))
                .isGreaterThan(0);

        documentSeeder.seed(DemoDataGenerator.generate(LocalDate.now(DemoDataGenerator.CLINIC_ZONE)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Integer.class)).isEqualTo(documents);
    }
}
