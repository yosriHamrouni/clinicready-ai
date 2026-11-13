package com.clinicready.readiness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.appointment.AppointmentService;
import com.clinicready.appointment.AppointmentType;
import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.common.NotFoundException;
import com.clinicready.readiness.dto.RequirementRuleRequest;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;

/**
 * Requirement rules of the current user's clinic. Anyone who may read appointments may read rules
 * (readiness checks need them); only {@link Permission#REQUIREMENT_RULES_MANAGE} may change them.
 * Every change writes an audit event in the same transaction.
 */
@Service
public class RequirementRuleService {

    static final String AUDIT_ENTITY = "REQUIREMENT_RULE";

    private static final Logger log = LoggerFactory.getLogger(RequirementRuleService.class);

    private final RequirementRuleRepository rules;
    private final AppointmentService appointments;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;
    private final AuditService audit;

    RequirementRuleService(RequirementRuleRepository rules, AppointmentService appointments,
            CurrentUserService currentUser, AuthorizationService authorization, AuditService audit) {
        this.rules = rules;
        this.appointments = appointments;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.audit = audit;
    }

    /**
     * Rules of one appointment type, by category.
     *
     * @throws NotFoundException type not in the clinic
     */
    @Transactional(readOnly = true)
    public List<RequirementRule> listForAppointmentType(UUID appointmentTypeId) {
        AppointmentType type = appointments.getType(appointmentTypeId);
        return rules.findAllByClinicIdAndAppointmentTypeIdOrderByDocumentCategory(currentUser.clinicId(),
                type.getId());
    }

    /**
     * @throws NotFoundException                type not in the clinic
     * @throws RequirementRuleConflictException the type already has a rule for the category
     */
    @Transactional
    public RequirementRule create(UUID appointmentTypeId, RequirementRuleRequest request) {
        authorization.require(Permission.REQUIREMENT_RULES_MANAGE);
        AppointmentType type = appointments.getType(appointmentTypeId);
        RequirementRule rule = new RequirementRule(currentUser.clinicId(), type.getId(), request.documentCategory(),
                request.mandatory(), request.validityDays(), request.needsVerification());
        requireUniqueCategory(rule, request);
        saveAndFlush(rule);
        audit.record(AuditAction.REQUIREMENT_RULE_CREATED, AUDIT_ENTITY, rule.getId(),
                summary(rule, null, rule.snapshot()));
        log.info("Requirement rule {} created for appointment type {}", rule.getId(), type.getId());
        return rule;
    }

    /**
     * Replaces every configurable field of the rule. A request that changes nothing writes nothing.
     *
     * @throws NotFoundException                rule not in the clinic
     * @throws RequirementRuleConflictException another rule of the type has the new category
     */
    @Transactional
    public RequirementRule update(UUID id, RequirementRuleRequest request) {
        authorization.require(Permission.REQUIREMENT_RULES_MANAGE);
        RequirementRule rule = find(id);
        Map<String, Object> before = rule.snapshot();
        if (before.equals(snapshotOf(request))) {
            return rule;
        }
        // Checked before the entity changes: the query would otherwise auto-flush the change first.
        requireUniqueCategory(rule, request);
        rule.update(request.documentCategory(), request.mandatory(), request.validityDays(),
                request.needsVerification());
        saveAndFlush(rule);
        audit.record(AuditAction.REQUIREMENT_RULE_UPDATED, AUDIT_ENTITY, rule.getId(),
                summary(rule, before, rule.snapshot()));
        log.info("Requirement rule {} updated", rule.getId());
        return rule;
    }

    /** @throws NotFoundException rule not in the clinic */
    @Transactional
    public void delete(UUID id) {
        authorization.require(Permission.REQUIREMENT_RULES_MANAGE);
        RequirementRule rule = find(id);
        rules.delete(rule);
        audit.record(AuditAction.REQUIREMENT_RULE_DELETED, AUDIT_ENTITY, rule.getId(),
                summary(rule, rule.snapshot(), null));
        log.info("Requirement rule {} deleted", rule.getId());
    }

    private RequirementRule find(UUID id) {
        return rules.findByIdAndClinicId(id, currentUser.clinicId())
                .orElseThrow(() -> new NotFoundException("Requirement rule"));
    }

    private void requireUniqueCategory(RequirementRule rule, RequirementRuleRequest request) {
        if (rules.existsByClinicIdAndAppointmentTypeIdAndDocumentCategoryAndIdNot(rule.getClinicId(),
                rule.getAppointmentTypeId(), request.documentCategory(), rule.getId())) {
            throw new RequirementRuleConflictException();
        }
    }

    /** The unique constraint still guards against two concurrent writers passing the check above. */
    private void saveAndFlush(RequirementRule rule) {
        try {
            rules.saveAndFlush(rule);
        } catch (DataIntegrityViolationException e) {
            throw new RequirementRuleConflictException();
        }
    }

    private static Map<String, Object> snapshotOf(RequirementRuleRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("documentCategory", request.documentCategory().name());
        fields.put("mandatory", request.mandatory());
        fields.put("validityDays", request.validityDays());
        fields.put("needsVerification", request.needsVerification());
        return fields;
    }

    private static Map<String, Object> summary(RequirementRule rule, Map<String, Object> before,
            Map<String, Object> after) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("appointmentTypeId", rule.getAppointmentTypeId().toString());
        summary.put("before", before);
        summary.put("after", after);
        return summary;
    }
}
