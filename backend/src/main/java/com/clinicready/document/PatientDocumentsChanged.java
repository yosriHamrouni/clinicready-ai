package com.clinicready.document;

import java.util.UUID;

/**
 * Published inside the transaction whenever something the readiness engine reads about a patient's
 * documents changes: upload, metadata correction, text extraction result, AI observation or review flag.
 * The readiness engine listens to it to recalculate the patient's appointments.
 */
public record PatientDocumentsChanged(UUID clinicId, UUID patientId) {
}
