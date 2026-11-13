package com.clinicready.patient;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinicready.patient.dto.NewPatient;
import com.clinicready.patient.dto.PatientResponse;

/** Patients. Permissions and clinic scoping are applied in {@link PatientService}. */
@RestController
@RequestMapping("/api/v1/patients")
class PatientController {

    private final PatientService patients;

    PatientController(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping
    List<PatientResponse> list(@RequestParam(required = false) String q) {
        return patients.search(q).stream().map(PatientResponse::from).toList();
    }

    @GetMapping("/{id}")
    PatientResponse get(@PathVariable UUID id) {
        return PatientResponse.from(patients.get(id));
    }

    @PostMapping
    ResponseEntity<PatientResponse> create(@Valid @RequestBody NewPatient request) {
        Patient patient = patients.create(request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patient.getId()))
                .body(PatientResponse.from(patient));
    }
}
