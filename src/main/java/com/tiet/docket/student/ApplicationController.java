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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.WorkflowService;

@Controller
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final DecisionHistoryRepository decisionHistoryRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final WorkflowService workflowService;
    private final FileStorageService fileStorageService;

    public ApplicationController(ApplicationRepository applicationRepository,
                                 UserRepository userRepository,
                                 DecisionHistoryRepository decisionHistoryRepository,
                                 ApplicationDocumentRepository applicationDocumentRepository,
                                 RequiredDocumentRepository requiredDocumentRepository,
                                 WorkflowService workflowService,
                                 FileStorageService fileStorageService) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.decisionHistoryRepository = decisionHistoryRepository;
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.workflowService = workflowService;
        this.fileStorageService = fileStorageService;
    }

    // Displays application details, uploaded documents, and full decision timeline
    @GetMapping("/student/applications/{id}")
    public String viewDetail(@PathVariable Long id, Authentication authentication, Model model) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        Application application = applicationRepository.findById(id).orElse(null);
        if (application == null || !application.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/dashboard";
        }

        List<ApplicationDocument> documents = applicationDocumentRepository.findByApplication(application);
        List<DecisionHistory> history = decisionHistoryRepository.findByApplicationOrderByDecidedAtAsc(application);

        // Find the latest correction requested reason if status is AWAITING_CORRECTION
        String latestCorrectionReason = null;
        if (application.getStatus() == ApplicationStatus.AWAITING_CORRECTION) {
            for (int i = history.size() - 1; i >= 0; i--) {
                DecisionHistory dh = history.get(i);
                if (dh.getDecision() == DecisionType.CORRECTION_REQUESTED) {
                    latestCorrectionReason = dh.getReason();
                    break;
                }
            }
        }

        model.addAttribute("application", application);
        model.addAttribute("documents", documents);
        model.addAttribute("history", history);
        model.addAttribute("latestCorrectionReason", latestCorrectionReason);

        return "student/application-detail";
    }

    // Displays the resubmit page when staff requested corrections
    @GetMapping("/student/applications/{id}/resubmit")
    public String showResubmit(@PathVariable Long id, Authentication authentication, Model model) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        Application application = applicationRepository.findById(id).orElse(null);
        if (application == null || !application.getStudent().getId().equals(student.getId())
                || application.getStatus() != ApplicationStatus.AWAITING_CORRECTION) {
            return "redirect:/student/dashboard";
        }

        List<DecisionHistory> history = decisionHistoryRepository.findByApplicationOrderByDecidedAtAsc(application);
        String latestCorrectionReason = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            DecisionHistory dh = history.get(i);
            if (dh.getDecision() == DecisionType.CORRECTION_REQUESTED) {
                latestCorrectionReason = dh.getReason();
                break;
            }
        }

        List<RequiredDocument> requiredDocs = requiredDocumentRepository.findByService(application.getService());
        List<ApplicationDocument> existingDocs = applicationDocumentRepository.findByApplication(application);

        model.addAttribute("application", application);
        model.addAttribute("latestCorrectionReason", latestCorrectionReason);
        model.addAttribute("requiredDocs", requiredDocs);
        model.addAttribute("existingDocs", existingDocs);

        return "student/application-resubmit";
    }

    // Handles document upload and workflow resubmission
    @PostMapping("/student/applications/{id}/resubmit")
    public String handleResubmit(@PathVariable Long id,
                                 Authentication authentication,
                                 MultipartHttpServletRequest request,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        User student = null;
        Application application = null;
        List<RequiredDocument> requiredDocs = null;
        String latestCorrectionReason = null;

        try {
            student = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

            application = applicationRepository.findById(id).orElse(null);
            if (application == null || !application.getStudent().getId().equals(student.getId())
                    || application.getStatus() != ApplicationStatus.AWAITING_CORRECTION) {
                return "redirect:/student/dashboard";
            }

            requiredDocs = requiredDocumentRepository.findByService(application.getService());
            List<DecisionHistory> history = decisionHistoryRepository.findByApplicationOrderByDecidedAtAsc(application);
            for (int i = history.size() - 1; i >= 0; i--) {
                DecisionHistory dh = history.get(i);
                if (dh.getDecision() == DecisionType.CORRECTION_REQUESTED) {
                    latestCorrectionReason = dh.getReason();
                    break;
                }
            }

            List<String> errors = new ArrayList<>();
            Map<RequiredDocument, MultipartFile> filesToSave = new LinkedHashMap<>();

            // Resubmit allows uploading replacement or additional documents (optional)
            for (RequiredDocument doc : requiredDocs) {
                MultipartFile file = request.getFile("doc_" + doc.getId());
                if (file != null && !file.isEmpty()) {
                    String validationError = fileStorageService.validate(file, doc.getDocumentName());
                    if (validationError != null) {
                        errors.add(validationError);
                    } else {
                        filesToSave.put(doc, file);
                    }
                }
            }

            if (!errors.isEmpty()) {
                model.addAttribute("application", application);
                model.addAttribute("latestCorrectionReason", latestCorrectionReason);
                model.addAttribute("requiredDocs", requiredDocs);
                model.addAttribute("existingDocs", applicationDocumentRepository.findByApplication(application));
                model.addAttribute("errors", errors);
                return "student/application-resubmit";
            }

            // Save new files and add ApplicationDocument rows
            for (Map.Entry<RequiredDocument, MultipartFile> entry : filesToSave.entrySet()) {
                RequiredDocument doc = entry.getKey();
                MultipartFile file = entry.getValue();

                String savedPath = fileStorageService.save(file);

                ApplicationDocument appDoc = new ApplicationDocument();
                appDoc.setApplication(application);
                appDoc.setRequiredDocument(doc);
                appDoc.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : doc.getDocumentName());
                appDoc.setFilePath(savedPath);
                appDoc.setFileType(file.getContentType());
                appDoc.setFileSize(file.getSize());
                applicationDocumentRepository.save(appDoc);
            }

            // Call workflowService.resubmit
            workflowService.resubmit(id, student);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Application " + application.getReferenceNumber() + " was resubmitted successfully.");
            return "redirect:/student/dashboard";

        } catch (IllegalStateException | IllegalArgumentException | IOException e) {
            model.addAttribute("application", application);
            model.addAttribute("latestCorrectionReason", latestCorrectionReason);
            model.addAttribute("requiredDocs", requiredDocs != null ? requiredDocs : List.of());
            model.addAttribute("existingDocs", application != null ? applicationDocumentRepository.findByApplication(application) : List.of());
            model.addAttribute("errors", List.of(e.getMessage() != null ? e.getMessage() : "An unexpected error occurred during resubmission."));
            return "student/application-resubmit";
        }
    }
}
