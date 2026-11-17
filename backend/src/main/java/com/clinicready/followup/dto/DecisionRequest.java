package com.clinicready.followup.dto;

/** Body of approve / reject. The comment is optional to approve, required to reject; at most 500 characters. */
public record DecisionRequest(String comment) {
}
