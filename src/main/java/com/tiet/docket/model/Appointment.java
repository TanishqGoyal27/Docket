package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// An in-person visit slot booked by a student (UC-01.6), e.g. to collect an ID card
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "application_id", unique = true)
    private Application application;     // one visit per application

    @Column(nullable = false, unique = true)
    private LocalDateTime slot;          // one student per slot

    private LocalDateTime bookedAt;

    @PrePersist
    protected void onCreate() {
        bookedAt = LocalDateTime.now();
    }

    public Appointment() {}

    public Long getId() { return id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public LocalDateTime getSlot() { return slot; }
    public void setSlot(LocalDateTime slot) { this.slot = slot; }

    public LocalDateTime getBookedAt() { return bookedAt; }
}
