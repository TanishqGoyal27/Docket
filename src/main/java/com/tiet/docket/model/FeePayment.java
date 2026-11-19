package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * The online application-fee payment for one application (D8 Payment Records).
 * The Finance Officer cross-checks these rows and confirms each payment.
 */
@Entity
@Table(name = "fee_payment")
public class FeePayment {

    public enum Status { CREATED, PAID }
    public enum Verification { PENDING, CONFIRMED, NOT_FOUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "application_id", unique = true)
    private Application application;

    @Column(nullable = false)
    private int amount;                  // rupees

    @Column(nullable = false)
    private String gateway;              // SANDBOX or RAZORPAY

    @Column(unique = true)
    private String orderId;              // from the gateway when checkout starts

    @Column(unique = true)
    private String paymentId;            // from the gateway after a successful payment

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CREATED;

    @Column(unique = true)
    private String receiptNumber;        // e.g. RCPT-2026-0001

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    private Verification verification;   // set once paid; Finance moves it on

    @ManyToOne
    @JoinColumn(name = "verified_by_id")
    private User verifiedBy;

    private LocalDateTime verifiedAt;

    @Column(length = 500)
    private String remark;               // Finance note, required when not found

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public FeePayment() {}

    public Long getId() { return id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public Verification getVerification() { return verification; }
    public void setVerification(Verification verification) { this.verification = verification; }

    public User getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(User verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
