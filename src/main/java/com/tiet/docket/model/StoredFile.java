package com.tiet.docket.model;

import jakarta.persistence.*;

// The bytes of one uploaded file, stored as a BLOB in the database (D3 Uploaded Documents)
@Entity
@Table(name = "stored_file")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = false, updatable = false)
    private byte[] content;              // never changed once saved (FR10)

    public StoredFile() {}

    public StoredFile(byte[] content) {
        this.content = content;
    }

    public Long getId() { return id; }
    public byte[] getContent() { return content; }
}
