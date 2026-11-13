package com.clinicready.appointment;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.clinicready.common.InvalidRequestException;

/**
 * Criteria of {@code GET /appointments}. Every field is optional, but the list must be bounded:
 * either a patient, or a {@code [from, to)} range of at most {@link #MAX_RANGE}.
 *
 * @param statuses empty means every status
 */
public record AppointmentFilter(Instant from, Instant to, UUID practitionerId, UUID patientId,
        Set<AppointmentStatus> statuses) {

    public static final Duration MAX_RANGE = Duration.ofDays(92);

    public AppointmentFilter {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }

    public static AppointmentFilter between(Instant from, Instant to) {
        return new AppointmentFilter(from, to, null, null, Set.of());
    }

    /** @throws InvalidRequestException if the filter could return an unbounded list */
    void validate() {
        if ((from == null) != (to == null)) {
            throw new InvalidRequestException("Give both from and to, or neither");
        }
        if (from == null && patientId == null) {
            throw new InvalidRequestException("Give a date range (from, to) or a patientId");
        }
        if (from != null && !from.isBefore(to)) {
            throw new InvalidRequestException("from must be before to");
        }
        if (from != null && Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("The date range is limited to " + MAX_RANGE.toDays() + " days");
        }
    }
}
