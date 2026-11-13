package com.clinicready.readiness;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinicready.readiness.dto.RequirementRuleRequest;
import com.clinicready.readiness.dto.RequirementRuleResponse;

/** Requirement rules per appointment type. Permissions are checked in {@link RequirementRuleService}. */
@RestController
@RequestMapping("/api/v1")
class RequirementRuleController {

    private final RequirementRuleService rules;

    RequirementRuleController(RequirementRuleService rules) {
        this.rules = rules;
    }

    @GetMapping("/appointment-types/{appointmentTypeId}/requirements")
    List<RequirementRuleResponse> list(@PathVariable UUID appointmentTypeId) {
        return rules.listForAppointmentType(appointmentTypeId).stream().map(RequirementRuleResponse::from).toList();
    }

    @PostMapping("/appointment-types/{appointmentTypeId}/requirements")
    ResponseEntity<RequirementRuleResponse> create(@PathVariable UUID appointmentTypeId,
            @Valid @RequestBody RequirementRuleRequest request) {
        RequirementRule rule = rules.create(appointmentTypeId, request);
        return ResponseEntity.created(URI.create("/api/v1/requirements/" + rule.getId()))
                .body(RequirementRuleResponse.from(rule));
    }

    @PutMapping("/requirements/{id}")
    RequirementRuleResponse update(@PathVariable UUID id, @Valid @RequestBody RequirementRuleRequest request) {
        return RequirementRuleResponse.from(rules.update(id, request));
    }

    @DeleteMapping("/requirements/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        rules.delete(id);
        return ResponseEntity.noContent().build();
    }
}
