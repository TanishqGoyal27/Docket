package com.tiet.docket.student;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

// Catches "file too large" errors that Spring raises before IdCardController runs
@ControllerAdvice
public class UploadErrorHandler {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final int maxSizeMb;

    public UploadErrorHandler(UserRepository userRepository,
                              ServiceRepository serviceRepository,
                              RequiredDocumentRepository requiredDocumentRepository,
                              @Value("${docket.upload.max-size-mb:10}") int maxSizeMb) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.maxSizeMb = maxSizeMb;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(MaxUploadSizeExceededException ex,
                                 HttpServletRequest request,
                                 Principal principal,
                                 Model model) {
        // If the upload error happened on another route, let default handling take over
        if (!request.getRequestURI().startsWith("/student/apply/id-card")) {
            throw ex;
        }

        User student = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));
        Service service = serviceRepository.findByName("ID Card")
                .orElseThrow(() -> new IllegalStateException("ID Card service is not configured"));
        List<RequiredDocument> documents = requiredDocumentRepository.findByService(service);

        model.addAttribute("student", student);
        model.addAttribute("documents", documents);
        model.addAttribute("errors", List.of(
                "One of your files is larger than " + maxSizeMb + " MB. Please upload smaller files."));
        return "student/id-card-form";
    }
}