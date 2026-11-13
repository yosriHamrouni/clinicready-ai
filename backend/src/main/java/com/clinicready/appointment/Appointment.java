package com.clinicready.appointment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One patient seeing one practitioner for one appointment type. Related rows are referenced by id;
 * the database rejects a reference to a row of another clinic.
 *
 * <p>The appointment is also the admission record: it carries the administrative checklist data
 * entered by staff (trusted contact, expected examinations, advance amount, payment confirmation).
 * A null value means "not provided yet". Confirmations are separate from the values they confirm
 * and are cleared whenever that value changes.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "appointment_type_id", nullable = false)
    private UUID appointmentTypeId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "trusted_contact_name")
    private String trustedContactName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trusted_contact_method")
    private ContactMethod trustedContactMethod;

    @Column(name = "trusted_contact_value")
    private String trustedContactValue;

    @Column(name = "trusted_contact_confirmed_by")
    private UUID trustedContactConfirmedBy;

    @Column(name = "trusted_contact_confirmed_at")
    private Instant trustedContactConfirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_examinations")
    private List<String> expectedExaminations;

    @Column(name = "advance_amount", precision = 12, scale = 3)
    private BigDecimal advanceAmount;

    @Column(name = "payment_confirmed_by")
    private UUID paymentConfirmedBy;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
    }

    public Appointment(UUID clinicId, UUID patientId, UUID practitionerId, UUID appointmentTypeId,
            Instant startsAt) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.appointmentTypeId = appointmentTypeId;
        this.startsAt = startsAt;
        this.status = AppointmentStatus.SCHEDULED;
        this.updatedAt = Instant.now();
    }

    /** Sets or clears the trusted contact. A different contact drops any earlier confirmation. */
    void setTrustedContact(String name, ContactMethod method, String value) {
        if (!Objects.equals(name, trustedContactName) || method != trustedContactMethod
                || !Objects.equals(value, trustedContactValue)) {
            trustedContactConfirmedBy = null;
            trustedContactConfirmedAt = null;
        }
        trustedContactName = name;
        trustedContactMethod = method;
        trustedContactValue = value;
        touch();
    }

    void confirmTrustedContact(UUID by, Instant at) {
        trustedContactConfirmedBy = by;
        trustedContactConfirmedAt = at;
        touch();
    }

    void setExpectedExaminations(List<String> examinations) {
        expectedExaminations = examinations == null ? null : List.copyOf(examinations);
        touch();
    }

    /** Sets or clears the advance. A different amount drops any earlier payment confirmation. */
    void setAdvanceAmount(BigDecimal amount) {
        if (!sameAmount(amount, advanceAmount)) {
            paymentConfirmedBy = null;
            paymentConfirmedAt = null;
        }
        advanceAmount = amount;
        touch();
    }

    void confirmPayment(UUID by, Instant at) {
        paymentConfirmedBy = by;
        paymentConfirmedAt = at;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getPractitionerId() {
        return practitionerId;
    }

    public UUID getAppointmentTypeId() {
        return appointmentTypeId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getTrustedContactName() {
        return trustedContactName;
    }

    public ContactMethod getTrustedContactMethod() {
        return trustedContactMethod;
    }

    public String getTrustedContactValue() {
        return trustedContactValue;
    }

    public UUID getTrustedContactConfirmedBy() {
        return trustedContactConfirmedBy;
    }

    public Instant getTrustedContactConfirmedAt() {
        return trustedContactConfirmedAt;
    }

    /** Null when not entered; empty when staff stated that no examination is expected. */
    public List<String> getExpectedExaminations() {
        return expectedExaminations;
    }

    public BigDecimal getAdvanceAmount() {
        return advanceAmount;
    }

    public UUID getPaymentConfirmedBy() {
        return paymentConfirmedBy;
    }

    public Instant getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
