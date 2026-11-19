package com.tiet.docket.student;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Checks uploads (QR4). The accepted files are stored in the database by SubmissionService.
@Service
public class FileStorageService {

    // Only these file types are allowed
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final long maxSizeBytes;

    // The limit comes from application.properties, so it is configurable
    public FileStorageService(@Value("${docket.upload.max-size-mb}") long maxSizeMb) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    // Returns a friendly error message, or null if the file is fine
    public String validate(MultipartFile file, String documentName) {
        if (file == null || file.isEmpty()) {
            return null; // nothing uploaded; the caller checks mandatory files
        }
        if (file.getSize() > maxSizeBytes) {
            return documentName + ": the file is too large. The maximum size is "
                    + (maxSizeBytes / 1024 / 1024) + " MB.";
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            return documentName + ": this file type is not allowed. Please upload a PDF, JPEG or PNG file.";
        }
        return null;
    }
}
