package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Change log for student record and configuration changes (FR40).
// Rows are only ever inserted; no code updates or deletes them (QR17).
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;                  // who made the change

    @Column(nullable = false, updatable = false)
    private String action;               // e.g. "USER_CREATED"

    @Column(length = 1000, updatable = false)
    private String details;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public AuditLog() {}

    public AuditLog(User actor, String action, String details) {
        this.actor = actor;
        this.action = action;
        this.details = details;
    }

    public Long getId() { return id; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
