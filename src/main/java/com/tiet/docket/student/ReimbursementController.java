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
import org.springframework.web.bind.annotation.RequestParam;
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
public class ReimbursementController {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final WorkflowService workflowService;
    private final FileStorageService fileStorageService;
    private final ApplicationDocumentRepository applicationDocumentRepository;

    public ReimbursementController(UserRepository userRepository,
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

    // Displays the Reimbursement application form
    @GetMapping("/student/apply/reimbursement")
    public String showForm(Authentication authentication, Model model) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        Service service = serviceRepository.findByName("Reimbursement")
                .orElseThrow(() -> new IllegalStateException("Reimbursement service is not configured"));

        List<RequiredDocument> documents = requiredDocumentRepository.findByService(service);

        model.addAttribute("student", student);
        model.addAttribute("documents", documents);

        return "student/reimbursement-form";
    }

    // Handles the submission of a Reimbursement claim
    @PostMapping("/student/apply/reimbursement")
    public String submitForm(Authentication authentication,
                             MultipartHttpServletRequest request,
                             @RequestParam(name = "amount", required = false) String amount,
                             @RequestParam(name = "description", required = false) String description,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User student = null;
        List<RequiredDocument> documents = null;

        try {
            student = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

            Service service = serviceRepository.findByName("Reimbursement")
                    .orElseThrow(() -> new IllegalStateException("Reimbursement service is not configured"));

            documents = requiredDocumentRepository.findByService(service);

            List<String> errors = new ArrayList<>();
            Map<RequiredDocument, MultipartFile> filesToSave = new LinkedHashMap<>();

            // Validate amount: must be a valid positive number
            if (amount == null || amount.trim().isBlank()) {
                errors.add("Amount is required.");
            } else {
                try {
                    double parsedAmount = Double.parseDouble(amount.trim());
                    if (parsedAmount <= 0) {
                        errors.add("Amount must be greater than 0.");
                    }
                } catch (NumberFormatException e) {
                    errors.add("Please enter a valid numeric amount.");
                }
            }

            // Validate description: must not be blank
            if (description == null || description.trim().isBlank()) {
                errors.add("Description is required.");
            }

            // Validate uploaded files and mandatory document requirements
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

            // Re-show form if any validation errors occurred
            if (!errors.isEmpty()) {
                model.addAttribute("student", student);
                model.addAttribute("documents", documents);
                model.addAttribute("errors", errors);
                model.addAttribute("amount", amount);
                model.addAttribute("description", description);
                return "student/reimbursement-form";
            }

            // Construct formData JSON: {"amount":"...","description":"..."}
            String safeAmount = escapeJson(amount != null ? amount.trim() : "");
            String safeDescription = escapeJson(description != null ? description.trim() : "");
            String formData = String.format("{\"amount\":\"%s\",\"description\":\"%s\"}", safeAmount, safeDescription);

            // Create Application and call workflowService.submit FIRST
            Application application = new Application();
            application.setStudent(student);
            application.setService(service);
            application.setFormData(formData);
            Application savedApp = workflowService.submit(application);

            // Save files to disk and persist ApplicationDocument records
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

            redirectAttributes.addFlashAttribute("successMessage",
                    "Application " + savedApp.getReferenceNumber() + " submitted successfully.");
            return "redirect:/student/dashboard";

        } catch (IllegalStateException | IllegalArgumentException | IOException e) {
            model.addAttribute("student", student);
            model.addAttribute("documents", documents != null ? documents : List.of());
            model.addAttribute("errors", List.of(e.getMessage() != null ? e.getMessage() : "An unexpected error occurred."));
            model.addAttribute("amount", amount);
            model.addAttribute("description", description);
            return "student/reimbursement-form";
        }
    }

    // Escapes special JSON characters (backslashes and double quotes)
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
