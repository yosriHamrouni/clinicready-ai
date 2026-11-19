package com.clinicready.readiness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.clinicready.readiness.dto.ReadinessCheckResponse;

/** The latest readiness result of one appointment, replaced on every recalculation. */
@Entity
@Table(name = "readiness_result")
class StoredReadiness {

    @Id
    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AggregateStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ReadinessCheckResponse> checks;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected StoredReadiness() {
    }

    StoredReadiness(UUID appointmentId, UUID clinicId) {
        this.appointmentId = appointmentId;
        this.clinicId = clinicId;
    }

    void replace(AggregateStatus status, List<ReadinessCheckResponse> checks, Instant evaluatedAt) {
        this.status = status;
        this.checks = List.copyOf(checks);
        this.evaluatedAt = evaluatedAt;
    }

    UUID getAppointmentId() {
        return appointmentId;
    }

    AggregateStatus getStatus() {
        return status;
    }

    List<ReadinessCheckResponse> getChecks() {
        return checks;
    }

    Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
