package com.tiet.docket.student;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.WorkflowService;

@Controller
public class IdCardController {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final WorkflowService workflowService;
    private final FileStorageService fileStorageService;
    private final ApplicationDocumentRepository applicationDocumentRepository;

    public IdCardController(UserRepository userRepository,
                            ServiceRepository serviceRepository,
                            RequiredDocumentRepository requiredDocumentRepository,
                            WorkflowService workflowService,
                            FileStorageService fileStorageService,
                            ApplicationDocumentRepository applicationDocumentRepository) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.workflowService = workflowService;
        this.fileStorageService = fileStorageService;
        this.applicationDocumentRepository = applicationDocumentRepository;
    }

    // Displays the ID Card application form with prefilled student details and document checklist
    @GetMapping("/student/apply/id-card")
    public String showForm(Authentication authentication, Model model) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        Service service = serviceRepository.findByName("ID Card")
                .orElseThrow(() -> new IllegalStateException("ID Card service is not configured"));

        List<RequiredDocument> documents = requiredDocumentRepository.findByService(service);

        model.addAttribute("student", student);
        model.addAttribute("documents", documents);

        return "student/id-card-form";
    }

    // Handles the multipart form submission for an ID Card application
    @PostMapping("/student/apply/id-card")
    public String submitForm(Authentication authentication,
                             MultipartHttpServletRequest request,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User student = null;
        List<RequiredDocument> documents = null;

        try {
            // 1. Get logged-in student, service, and required documents
            student = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

            Service service = serviceRepository.findByName("ID Card")
                    .orElseThrow(() -> new IllegalStateException("ID Card service is not configured"));

            documents = requiredDocumentRepository.findByService(service);

            List<String> errors = new ArrayList<>();
            Map<RequiredDocument, MultipartFile> filesToSave = new LinkedHashMap<>();

            // 2, 3, 4. Validate each uploaded file and verify mandatory documents are present
            for (RequiredDocument doc : documents) {
                MultipartFile file = request.getFile("doc_" + doc.getId());
                if (file != null && !file.isEmpty()) {
                    String validationError = fileStorageService.validate(file, doc.getDocumentName());
                    if (validationError != null) {
                        errors.add(validationError);
                    } else {
                        filesToSave.put(doc, file);
                    }
                } else if (doc.isMandatory()) {
                    errors.add(doc.getDocumentName() + " is required.");
                }
            }

            // 5. If any validation errors exist, re-show the form without throwing a 500 error
            if (!errors.isEmpty()) {
                model.addAttribute("student", student);
                model.addAttribute("documents", documents);
                model.addAttribute("errors", errors);
                return "student/id-card-form";
            }

            // 6. Create application and call workflowService.submit FIRST to generate reference number
            Application application = new Application();
            application.setStudent(student);
            application.setService(service);
            Application savedApp = workflowService.submit(application);

            // 7. Save each uploaded file to disk and record its metadata in the database
            for (Map.Entry<RequiredDocument, MultipartFile> entry : filesToSave.entrySet()) {
                RequiredDocument doc = entry.getKey();
                MultipartFile file = entry.getValue();

                String savedPath = fileStorageService.save(file);

                ApplicationDocument appDoc = new ApplicationDocument();
                appDoc.setApplication(savedApp);
                appDoc.setRequiredDocument(doc);
                appDoc.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : doc.getDocumentName());
                appDoc.setFilePath(savedPath);
                appDoc.setFileType(file.getContentType());
                appDoc.setFileSize(file.getSize());
                applicationDocumentRepository.save(appDoc);
            }

            // 9. Flash success message with the reference number and redirect to student dashboard
            redirectAttributes.addFlashAttribute("successMessage",
                    "Application " + savedApp.getReferenceNumber() + " submitted successfully.");
            return "redirect:/student/dashboard";

        } catch (IllegalStateException | IllegalArgumentException | IOException e) {
            // 8. Catch exceptions and re-show the form with friendly error messages
            model.addAttribute("student", student);
            model.addAttribute("documents", documents != null ? documents : List.of());
            model.addAttribute("errors", List.of(e.getMessage() != null ? e.getMessage() : "An unexpected error occurred."));
            return "student/id-card-form";
        }
    }
}
