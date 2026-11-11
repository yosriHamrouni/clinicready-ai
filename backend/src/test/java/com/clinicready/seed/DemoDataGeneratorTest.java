package com.clinicready.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoDataGeneratorTest {

    // A Wednesday, so "tomorrow" is a normal working day.
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    private final DemoData data = DemoDataGenerator.generate(TODAY);

    private static LocalDate dayOf(DemoData.Appointment a) {
        return a.startsAt().atZoneSameInstant(DemoDataGenerator.CLINIC_ZONE).toLocalDate();
    }

    @Test
    void buildsTheExpectedClinic() {
        assertThat(data.clinic().name()).isEqualTo("Clinique Démo Nabeul");
        assertThat(data.clinic().city()).isEqualTo("Nabeul");
        assertThat(data.practitioners()).hasSize(2);
        assertThat(data.practitioners()).anyMatch(p -> p.fullName().contains("Ben Salem"));
        assertThat(data.patients()).hasSize(40);
        assertThat(data.appointmentTypes()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void hasOneUserForEveryRoleAndPractitionerUsersAreLinked() {
        assertThat(data.users().stream().map(DemoData.User::role).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("RECEPTIONIST", "PRACTITIONER", "CLINIC_MANAGER");
        Set<UUID> practitionerIds =
                data.practitioners().stream().map(DemoData.Practitioner::id).collect(Collectors.toSet());
        assertThat(data.users()).filteredOn(u -> u.role().equals("PRACTITIONER"))
                .hasSize(2)
                .allSatisfy(u -> assertThat(practitionerIds).contains(u.practitionerId()));
        assertThat(data.users()).filteredOn(u -> !u.role().equals("PRACTITIONER"))
                .allSatisfy(u -> assertThat(u.practitionerId()).isNull());
    }

    @Test
    void everythingIsClearlyFictional() {
        assertThat(data.users()).allSatisfy(u -> assertThat(u.email()).endsWith("@nabeul-demo.test"));
        assertThat(data.patients()).allSatisfy(p -> {
            assertThat(p.ref()).startsWith("DEMO-");
            assertThat(p.phone()).startsWith("+216 00 "); // not a valid Tunisian prefix
        });
    }

    @Test
    void patientsAreUniqueAndBilingual() {
        assertThat(data.patients().stream().map(DemoData.Patient::ref).distinct()).hasSize(40);
        assertThat(data.patients().stream().map(DemoData.Patient::fullName).distinct()).hasSize(40);
        assertThat(data.patients().stream().map(DemoData.Patient::id).distinct()).hasSize(40);

        var arabic = data.patients().stream().filter(p -> p.language().equals("AR")).toList();
        var french = data.patients().stream().filter(p -> p.language().equals("FR")).toList();
        assertThat(arabic).isNotEmpty()
                .allSatisfy(p -> assertThat(p.fullName()).matches(".*\\p{InArabic}.*"));
        assertThat(french).isNotEmpty()
                .allSatisfy(p -> assertThat(p.fullName()).doesNotContainPattern("\\p{InArabic}"));
    }

    @Test
    void tomorrowHasAppointmentsAndNothingFallsOnSunday() {
        long tomorrowCount =
                data.appointments().stream().filter(a -> dayOf(a).equals(TODAY.plusDays(1))).count();
        assertThat(tomorrowCount).isGreaterThanOrEqualTo(10);
        assertThat(data.appointments())
                .allSatisfy(a -> assertThat(dayOf(a).getDayOfWeek()).isNotEqualTo(DayOfWeek.SUNDAY));
    }

    @Test
    void noPractitionerIsDoubleBookedAndStatusMatchesTheDate() {
        Set<String> slots = new HashSet<>();
        for (var a : data.appointments()) {
            assertThat(slots.add(a.practitionerId() + "@" + a.startsAt())).as("double booking").isTrue();
            if (dayOf(a).isBefore(TODAY)) {
                assertThat(a.status()).isIn("COMPLETED", "NO_SHOW", "CANCELLED");
            } else {
                assertThat(a.status()).isIn("SCHEDULED", "CANCELLED");
            }
        }
    }

    @Test
    void isDeterministicAndAnchoredToToday() {
        assertThat(DemoDataGenerator.generate(TODAY)).isEqualTo(data);
        // Same ids on another day (so re-seeding updates in place), different dates.
        DemoData later = DemoDataGenerator.generate(TODAY.plusDays(7));
        assertThat(later.appointments().stream().map(DemoData.Appointment::id).collect(Collectors.toSet()))
                .isEqualTo(
                        data.appointments().stream().map(DemoData.Appointment::id).collect(Collectors.toSet()));
        assertThat(later.appointments()).isNotEqualTo(data.appointments());
    }

    @Test
    void sundayStillAnchorsWithoutCollisions() {
        DemoData onSunday = DemoDataGenerator.generate(LocalDate.of(2026, 6, 14));
        Set<String> slots = new HashSet<>();
        assertThat(onSunday.appointments())
                .allSatisfy(a -> assertThat(slots.add(a.practitionerId() + "@" + a.startsAt())).isTrue());
    }

    @Test
    void demoPasswordHashMatchesDocumentedPassword() {
        assertThat(new BCryptPasswordEncoder().matches("password", DemoDataSeeder.DEMO_PASSWORD_HASH))
                .isTrue();
    }
}
