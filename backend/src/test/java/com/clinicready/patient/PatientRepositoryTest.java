package com.clinicready.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.clinicready.RepositoryTest;
import com.clinicready.clinic.Clinic;
import com.clinicready.common.Language;

@RepositoryTest
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patients;

    @Autowired
    private TestEntityManager em;

    private UUID clinicA;
    private UUID clinicB;

    @BeforeEach
    void createClinics() {
        clinicA = em.persist(new Clinic("Clinique A", "Nabeul")).getId();
        clinicB = em.persist(new Clinic("Clinique B", "Hammamet")).getId();
    }

    @Test
    void savesAndReadsBackAllFields() {
        Patient saved = patients.saveAndFlush(
                new Patient(clinicA, "P-0001", "Amel Test", "+216 20 000 001", Language.AR));
        em.clear();

        Patient found = patients.findByIdAndClinicId(saved.getId(), clinicA).orElseThrow();

        assertThat(found.getPatientRef()).isEqualTo("P-0001");
        assertThat(found.getFullName()).isEqualTo("Amel Test");
        assertThat(found.getPhone()).isEqualTo("+216 20 000 001");
        assertThat(found.getPreferredLanguage()).isEqualTo(Language.AR);
    }

    @Test
    void lookupsNeverReturnAnotherClinicsPatient() {
        Patient ofA = patients.saveAndFlush(new Patient(clinicA, "P-0001", "Amel Test", null, Language.FR));

        assertThat(patients.findByIdAndClinicId(ofA.getId(), clinicB)).isEmpty();
        assertThat(patients.findByClinicIdAndPatientRef(clinicB, "P-0001")).isEmpty();
        assertThat(patients.findAllByClinicIdOrderByPatientRef(clinicB)).isEmpty();
    }

    @Test
    void listsOwnPatientsOrderedByRef() {
        patients.save(new Patient(clinicA, "P-0002", "Second", null, Language.FR));
        patients.save(new Patient(clinicA, "P-0001", "First", null, Language.FR));
        patients.saveAndFlush(new Patient(clinicB, "P-0003", "Other clinic", null, Language.FR));

        assertThat(patients.findAllByClinicIdOrderByPatientRef(clinicA))
                .extracting(Patient::getPatientRef)
                .containsExactly("P-0001", "P-0002");
    }

    @Test
    void patientRefIsUniquePerClinicOnly() {
        patients.saveAndFlush(new Patient(clinicA, "P-0001", "Amel Test", null, Language.FR));
        patients.saveAndFlush(new Patient(clinicB, "P-0001", "Same ref, other clinic", null, Language.FR));

        assertThatThrownBy(() -> patients.saveAndFlush(
                new Patient(clinicA, "P-0001", "Duplicate", null, Language.FR)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
