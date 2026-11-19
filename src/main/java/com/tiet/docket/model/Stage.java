package com.tiet.docket.model;

import jakarta.persistence.*;

@Entity
@Table(name = "stage")
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "service_id")
    private Service service;             // which service this step belongs to

    @Column(nullable = false)
    private int stepOrder;               // 1, 2, 3... order of the steps

    @Column(nullable = false)
    private String name;                 // e.g. "Fee verification"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role handlerRole;            // which role handles this step

    private String department;           // optional, narrows it down

    // An optional stage is used only when the previous officer chooses to forward to it
    // (e.g. Finance review of a scholarship "where required")
    private boolean optional = false;

    public Stage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Role getHandlerRole() { return handlerRole; }
    public void setHandlerRole(Role handlerRole) { this.handlerRole = handlerRole; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }
}