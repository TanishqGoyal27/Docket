package com.tiet.docket.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores a file that a student has uploaded for a specific application.
 * Linked to the application and optionally to a RequiredDocument so we can
 * tell which checklist item it satisfies.
 */
@Entity
@Table(name = "application_document")
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The application this file belongs to. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    /**
     * Which required-document checklist item this file satisfies.
     * Optional: a student can upload extra files that are not on the list.
     */
    @ManyToOne
    @JoinColumn(name = "required_document_id")
    private RequiredDocument requiredDocument;

    /** Original file name as supplied by the browser (e.g. "photo.jpg"). */
    @Column(nullable = false)
    private String fileName;

    /**
     * Path on the server where the file is saved.
     * (e.g. "/uploads/applications/2026/photo.jpg")
     */
    @Column(nullable = false)
    private String filePath;

    /** MIME type (e.g. "image/jpeg", "application/pdf"). */
    private String fileType;

    /** File size in bytes. */
    private long fileSize;

    /** Set automatically when the row is first created. */
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    // ----------------------------------------------------------------
    // No-arg constructor (required by JPA)
    // ----------------------------------------------------------------
    public ApplicationDocument() {}

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public RequiredDocument getRequiredDocument() { return requiredDocument; }
    public void setRequiredDocument(RequiredDocument requiredDocument) {
        this.requiredDocument = requiredDocument;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
