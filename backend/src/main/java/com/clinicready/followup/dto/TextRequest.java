package com.clinicready.followup.dto;

/** Body of {@code PUT /follow-up-tasks/{id}/draft} and {@code PUT /reminder-templates/{code}/{language}}. */
public record TextRequest(String body) {
}
