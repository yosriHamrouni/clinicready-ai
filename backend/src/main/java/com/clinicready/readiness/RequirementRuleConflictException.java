package com.clinicready.readiness;

import org.springframework.http.HttpStatus;

import com.clinicready.common.ApiException;

/** The appointment type already has a rule for this document category. */
public class RequirementRuleConflictException extends ApiException {

    public RequirementRuleConflictException() {
        super(HttpStatus.CONFLICT, "RULE_CONFLICT",
                "This appointment type already has a rule for this document category");
    }
}
