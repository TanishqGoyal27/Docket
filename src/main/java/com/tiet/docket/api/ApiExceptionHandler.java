package com.tiet.docket.api;

import com.tiet.docket.student.SubmissionService.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** Turns business-rule exceptions into JSON the React app can show as a message. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(ValidationException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Please fix the problems below.",
                "errors", e.getErrors()));
    }

    // Workflow and admin rules throw these with a message meant for the user
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> rule(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "errors", List.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of(
                "error", "The upload is too large. Each file may be at most 10 MB.", "errors", List.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", message, "errors", List.of()));
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, Object>> io(UncheckedIOException e) {
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage(), "errors", List.of()));
    }
}
