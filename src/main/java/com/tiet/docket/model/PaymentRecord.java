package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Records the payment reference collected by an Accounts Officer before
 * a Reimbursement (or any payment-stage) application can be marked PROCESSED.
 *
 * One application has at most one PaymentRecord (enforced by the repository
 * check in WorkflowService.recordPaymentAndProcess).
 */
@Entity
@Table(name = "payment_record")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The application this payment belongs to (required)
    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    // The bank / challan / UTR reference supplied by the officer (required)
    @Column(nullable = false)
    private String paymentReference;

    // The date on which the payment was made (required)
    @Column(nullable = false)
    private LocalDate paymentDate;

    // Which staff member recorded this payment (required)
    @ManyToOne(optional = false)
    @JoinColumn(name = "recorded_by_id")
    private User recordedBy;

    // Timestamp set automatically when this row is first saved
    @Column(updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
    }

    public PaymentRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}