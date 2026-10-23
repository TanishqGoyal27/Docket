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

    public Service() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}