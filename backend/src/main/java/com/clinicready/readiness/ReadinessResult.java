package com.clinicready.readiness;

import java.util.List;

/** Aggregate status plus every per-requirement check, optional ones included. */
public record ReadinessResult(AggregateStatus status, List<ReadinessCheck> checks) {

    public ReadinessResult {
        checks = List.copyOf(checks);
    }
}
