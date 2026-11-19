package com.tiet.docket.model;

import jakarta.persistence.*;

@Entity
@Table(name = "services")   // plural, to avoid clashes with database keywords
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;                 // e.g. "ID Card", "Reimbursement"

    @Column(length = 1000)
    private String description;          // shown to students

    private boolean active = true;       // admin can switch a service on or off

    // Indicates whether approving the final stage should update student details
    @Column(name = "updates_student_record")
    private boolean updatesStudentRecord = false;

    // Status given when the last stage approves: APPROVED, or VERIFIED for verification services
    @Enumerated(EnumType.STRING)
    private ApplicationStatus finalStatus = ApplicationStatus.APPROVED;

    // True if an approved application needs an in-person visit slot (ID card collection)
    private boolean requiresVisit = false;

    // Application fee in rupees, paid online before the application is submitted (0 = free)
    private int feeAmount = 0;

    // The teacher named by the student must approve by email before staff see the application
    private boolean requiresSupervisorApproval = false;

    public Service() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isUpdatesStudentRecord() { return updatesStudentRecord; }
    public void setUpdatesStudentRecord(boolean updatesStudentRecord) { this.updatesStudentRecord = updatesStudentRecord; }

    public ApplicationStatus getFinalStatus() { return finalStatus == null ? ApplicationStatus.APPROVED : finalStatus; }
    public void setFinalStatus(ApplicationStatus finalStatus) { this.finalStatus = finalStatus; }

    public boolean isRequiresVisit() { return requiresVisit; }
    public void setRequiresVisit(boolean requiresVisit) { this.requiresVisit = requiresVisit; }

    public int getFeeAmount() { return feeAmount; }
    public void setFeeAmount(int feeAmount) { this.feeAmount = feeAmount; }

    public boolean isRequiresSupervisorApproval() { return requiresSupervisorApproval; }
    public void setRequiresSupervisorApproval(boolean requiresSupervisorApproval) {
        this.requiresSupervisorApproval = requiresSupervisorApproval;
    }
}