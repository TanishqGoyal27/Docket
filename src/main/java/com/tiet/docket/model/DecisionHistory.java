package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "decision_history")
public class DecisionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", updatable = false)
    private Application application;

    @ManyToOne
    @JoinColumn(name = "stage_id", updatable = false)
    private Stage stage;                 // the step the decision was made at

    @ManyToOne
    @JoinColumn(name = "officer_id", updatable = false)
    private User officer;                // who decided (empty for an email approval)

    @Column(updatable = false)
    private String externalActor;        // e.g. "Dr. A. Sharma (a.sharma@thapar.edu)" for a teacher's email approval

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DecisionType decision;

    @Column(length = 1000, updatable = false)
    private String reason;               // mandatory for rejection

    @Column(updatable = false)
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        decidedAt = LocalDateTime.now();
    }

    public DecisionHistory() {}

    public Long getId() { return id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }

    public String getExternalActor() { return externalActor; }
    public void setExternalActor(String externalActor) { this.externalActor = externalActor; }

    public User getOfficer() { return officer; }
    public void setOfficer(User officer) { this.officer = officer; }

    public DecisionType getDecision() { return decision; }
    public void setDecision(DecisionType decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
}