package com.tiet.docket.student;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    // Only these file types are allowed
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final long maxSizeBytes;
    private final Path uploadDir;

    // The limits come from application.properties, so they are configurable
    public FileStorageService(@Value("${docket.upload.max-size-mb}") long maxSizeMb,
            @Value("${docket.upload.dir}") String dir) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    // Returns a friendly error message, or null if the file is fine
    public String validate(MultipartFile file, String documentName) {
        if (file == null || file.isEmpty()) {
            return null; // nothing uploaded; the controller checks mandatory files
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

    // Saves the file with a safe, unique name and returns the path where it was
    // saved
    public String save(MultipartFile file) throws IOException {
        Files.createDirectories(uploadDir);

        // The original name is never used for the path (avoids path tricks)
        String type = file.getContentType();
        String extension = "application/pdf".equals(type) ? ".pdf"
                : "image/png".equals(type) ? ".png" : ".jpg";
        String storedName = UUID.randomUUID() + extension;

        Path target = uploadDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toString();
    }
}