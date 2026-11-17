package com.clinicready.agent.dto;

/** Body of {@code POST /agent-runs}: the request in the user's own words, at most 500 characters. */
public record NewAgentRun(String request) {
}
