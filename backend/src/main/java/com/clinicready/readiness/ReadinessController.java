package com.clinicready.readiness;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.readiness.dto.ReadinessResponse;

/** Readiness of one appointment. Access and scoping are applied in {@link ReadinessService}. */
@RestController
@RequestMapping("/api/v1/appointments/{id}")
class ReadinessController {

    private final ReadinessService readiness;

    ReadinessController(ReadinessService readiness) {
        this.readiness = readiness;
    }

    @PostMapping("/readiness-checks")
    ReadinessResponse run(@PathVariable UUID id) {
        return readiness.run(id);
    }

    @GetMapping("/readiness")
    ReadinessResponse latest(@PathVariable UUID id) {
        return readiness.latest(id);
    }
}
