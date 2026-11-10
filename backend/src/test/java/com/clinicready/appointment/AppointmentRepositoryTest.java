package com.clinicready.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.clinicready.RepositoryTest;
import com.clinicready.clinic.Clinic;
import com.clinicready.common.Language;
import com.clinicready.patient.Patient;

@RepositoryTest
class AppointmentRepositoryTest {

    private static final Instant DAY_START = Instant.parse("2026-11-12T00:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-11-13T00:00:00Z");

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private PractitionerRepository practitioners;

    @Autowired
    private AppointmentTypeRepository types;

    @Autowired
    private TestEntityManager em;

    private Tenant a;
    private Tenant b;

    @BeforeEach
    void createTenants() {
        a = tenant("Clinique A");
        b = tenant("Clinique B");
    }

    @Test
    void savesAndReadsBackAllFields() {
        Instant startsAt = Instant.parse("2026-11-12T09:30:00Z");
        Appointment saved = appointments.saveAndFlush(a.appointment(a.practitioner, startsAt));
        em.clear();

        Appointment found = appointments.findByIdAndClinicId(saved.getId(), a.clinicId).orElseThrow();

        assertThat(found.getPatientId()).isEqualTo(a.patientId);
        assertThat(found.getPractitionerId()).isEqualTo(a.practitioner);
        assertThat(found.getAppointmentTypeId()).isEqualTo(a.typeId);
        assertThat(found.getStartsAt()).isEqualTo(startsAt);
        assertThat(found.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void findStartingBetweenReturnsOwnClinicInRangeOrdered() {
        Appointment late = appointments.save(a.appointment(a.practitioner, Instant.parse("2026-11-12T15:00:00Z")));
        Appointment early = appointments.save(a.appointment(a.practitioner, Instant.parse("2026-11-12T08:00:00Z")));
        appointments.save(a.appointment(a.practitioner, DAY_END));
        appointments.save(a.appointment(a.practitioner, DAY_START.minusSeconds(1)));
        appointments.saveAndFlush(b.appointment(b.practitioner, Instant.parse("2026-11-12T10:00:00Z")));

        assertThat(appointments.findStartingBetween(a.clinicId, DAY_START, DAY_END))
                .extracting(Appointment::getId)
                .containsExactly(early.getId(), late.getId());
    }

    @Test
    void findStartingBetweenForPractitionerFiltersByPractitioner() {
        UUID otherPractitioner = practitioners.save(new Practitioner(a.clinicId, "Dr Other", null)).getId();
        Appointment mine = appointments.save(a.appointment(a.practitioner, Instant.parse("2026-11-12T09:00:00Z")));
        appointments.saveAndFlush(a.appointment(otherPractitioner, Instant.parse("2026-11-12T10:00:00Z")));

        assertThat(appointments.findStartingBetweenForPractitioner(a.clinicId, a.practitioner, DAY_START, DAY_END))
                .extracting(Appointment::getId)
                .containsExactly(mine.getId());
        assertThat(appointments.findStartingBetweenForPractitioner(b.clinicId, a.practitioner, DAY_START, DAY_END))
                .isEmpty();
    }

    @Test
    void lookupByIdNeverReturnsAnotherClinicsAppointment() {
        Appointment ofA = appointments.saveAndFlush(a.appointment(a.practitioner, DAY_START));

        assertThat(appointments.findByIdAndClinicId(ofA.getId(), b.clinicId)).isEmpty();
    }

    @Test
    void appointmentCannotReferencePatientOfAnotherClinic() {
        Appointment crossTenant = new Appointment(b.clinicId, a.patientId, b.practitioner, b.typeId, DAY_START);

        assertThatThrownBy(() -> appointments.saveAndFlush(crossTenant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appointmentCannotReferencePractitionerOfAnotherClinic() {
        Appointment crossTenant = new Appointment(b.clinicId, b.patientId, a.practitioner, b.typeId, DAY_START);

        assertThatThrownBy(() -> appointments.saveAndFlush(crossTenant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appointmentCannotReferenceTypeOfAnotherClinic() {
        Appointment crossTenant = new Appointment(b.clinicId, b.patientId, b.practitioner, a.typeId, DAY_START);

        assertThatThrownBy(() -> appointments.saveAndFlush(crossTenant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appointmentTypeCodeIsUniquePerClinicOnly() {
        assertThat(types.findByClinicIdAndCode(a.clinicId, "CONSULT")).isPresent();
        assertThat(types.findByClinicIdAndCode(b.clinicId, "CONSULT")).isPresent();

        assertThatThrownBy(() -> types.saveAndFlush(new AppointmentType(a.clinicId, "CONSULT", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void practitionersAndTypesAreListedPerClinic() {
        assertThat(practitioners.findAllByClinicIdOrderByFullName(a.clinicId))
                .extracting(Practitioner::getId)
                .containsExactly(a.practitioner);
        assertThat(practitioners.findByIdAndClinicId(a.practitioner, b.clinicId)).isEmpty();
        assertThat(types.findAllByClinicIdOrderByCode(b.clinicId))
                .extracting(AppointmentType::getId)
                .containsExactly(b.typeId);
        assertThat(types.findByIdAndClinicId(a.typeId, b.clinicId)).isEmpty();
    }

    private Tenant tenant(String name) {
        UUID clinicId = em.persist(new Clinic(name, "Nabeul")).getId();
        UUID patientId = em.persist(new Patient(clinicId, "P-0001", "Patient Test", null, Language.FR)).getId();
        UUID practitionerId = practitioners.save(new Practitioner(clinicId, "Dr Test", "Radiologie")).getId();
        UUID typeId = types.save(new AppointmentType(clinicId, "CONSULT", "Consultation")).getId();
        em.flush();
        return new Tenant(clinicId, patientId, practitionerId, typeId);
    }

    private record Tenant(UUID clinicId, UUID patientId, UUID practitioner, UUID typeId) {

        Appointment appointment(UUID practitionerId, Instant startsAt) {
            return new Appointment(clinicId, patientId, practitionerId, typeId, startsAt);
        }
    }
}
