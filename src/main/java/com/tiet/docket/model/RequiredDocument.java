package com.tiet.docket.model;

import jakarta.persistence.*;

/**
 * Represents a document that a student must upload when submitting a
 * particular service.  Rows are created by WorkflowDataSeeder.
 * For example, "Passport-size photo" is one row for the "ID Card" service.
 */
@Entity
@Table(name = "required_document")
public class RequiredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The service this document belongs to (e.g. "ID Card"). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "service_id")
    private Service service;

    /** Human-readable label shown to the student (e.g. "Fee payment receipt"). */
    @Column(nullable = false)
    private String documentName;

    /**
     * If true, the student MUST upload this document to submit.
     * If false, it is optional (but still shown in the checklist).
     */
    private boolean mandatory = true;

    // ----------------------------------------------------------------
    // No-arg constructor (required by JPA)
    // ----------------------------------------------------------------
    public RequiredDocument() {}

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
