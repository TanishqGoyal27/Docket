package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * The teacher (project supervisor) named by the student, who approves the
 * claim from a link in an email instead of logging in. Only a hash of the
 * link's token is stored, so the database alone cannot be used to approve.
 */
@Entity
@Table(name = "supervisor_approval")
public class SupervisorApproval {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "application_id", unique = true)
    private Application application;

    @Column(nullable = false)
    private String teacherName;

    @Column(nullable = false)
    private String teacherDepartment;

    @Column(nullable = false)
    private String teacherEmail;

    @Column(nullable = false, unique = true)
    private String tokenHash;            // SHA-256 of the secret in the email link

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(length = 1000)
    private String remark;               // teacher's comment; required when rejecting

    private LocalDateTime emailSentAt;
    private LocalDateTime decidedAt;

    public SupervisorApproval() {}

    public Long getId() { return id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getTeacherDepartment() { return teacherDepartment; }
    public void setTeacherDepartment(String teacherDepartment) { this.teacherDepartment = teacherDepartment; }

    public String getTeacherEmail() { return teacherEmail; }
    public void setTeacherEmail(String teacherEmail) { this.teacherEmail = teacherEmail; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getEmailSentAt() { return emailSentAt; }
    public void setEmailSentAt(LocalDateTime emailSentAt) { this.emailSentAt = emailSentAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
