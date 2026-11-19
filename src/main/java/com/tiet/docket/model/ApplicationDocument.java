package com.tiet.docket.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
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
     * The uploaded bytes, kept in the database (stored_file table). Loaded only
     * when the file is downloaded, so listing documents stays fast.
     */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "file_id")
    private StoredFile file;

    /** True for the receipt Docket generates after payment; only the student and Finance see it. */
    private boolean feeReceipt = false;

    /** True once the student uploads a newer file for the same checklist item on a correction. */
    @ColumnDefault("false")   // lets the column be added to a database that already has documents
    private boolean replaced = false;

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

    public boolean isReplaced() { return replaced; }
    public void setReplaced(boolean replaced) { this.replaced = replaced; }

    public boolean isFeeReceipt() { return feeReceipt; }
    public void setFeeReceipt(boolean feeReceipt) { this.feeReceipt = feeReceipt; }

    public byte[] getData() { return file == null ? null : file.getContent(); }
    public void setData(byte[] data) { this.file = new StoredFile(data); }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
