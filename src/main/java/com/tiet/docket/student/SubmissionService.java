package com.tiet.docket.student;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.workflow.SupervisorApprovalService;
import com.tiet.docket.workflow.WorkflowService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One submission path for every service (FR35, FR36): validates the
 * service-specific fields and the configured required documents, then hands
 * the application to the workflow engine. A new service added by the
 * Administrator works here without code changes (only "details" is asked).
 */
@org.springframework.stereotype.Service
public class SubmissionService {

    // Required form fields per service; services not listed only need their documents
    private static final Map<String, List<String>> REQUIRED_FIELDS = Map.of(
            "ID Card", List.of("requestType", "reason"),
            "Reimbursement", List.of("projectTitle", "teacherName", "teacherDepartment", "teacherEmail",
                    "amount", "description"),
            "Details Correction", List.of("field", "newValue", "reason"),
            "Document Verification", List.of("documentType", "purpose"),
            "Scholarship Verification", List.of("scholarshipName", "familyIncome"));
    private static final Set<String> NUMBER_FIELDS = Set.of("amount", "familyIncome");
    private static final Set<String> CORRECTABLE_FIELDS = Set.of("name", "phone", "course", "rollNumber");
    private static final int MAX_FORM_DATA = 4000;   // Application.formData column length

    // A lost ID card can only be replaced with a police FIR copy
    public static final String FIR_DOCUMENT = "FIR copy (required if the card was lost)";
    public static final String LOST_CARD = "Replacement (lost)";

    private final ServiceRepository serviceRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final SupervisorApprovalService supervisorApprovalService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubmissionService(ServiceRepository serviceRepository,
                             RequiredDocumentRepository requiredDocumentRepository,
                             ApplicationRepository applicationRepository,
                             ApplicationDocumentRepository applicationDocumentRepository,
                             FileStorageService fileStorageService,
                             WorkflowService workflowService,
                             SupervisorApprovalService supervisorApprovalService) {
        this.serviceRepository = serviceRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.applicationRepository = applicationRepository;
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.workflowService = workflowService;
        this.supervisorApprovalService = supervisorApprovalService;
    }

    public static List<String> requiredFields(String serviceName) {
        return REQUIRED_FIELDS.getOrDefault(serviceName, List.of());
    }

    @Transactional
    public Application submit(User student, Long serviceId, Map<String, String> fields,
                              Function<String, MultipartFile> files) {
        Service service = serviceRepository.findById(serviceId)
                .filter(Service::isActive)
                .orElseThrow(() -> new IllegalArgumentException("This service is not available"));
        List<RequiredDocument> documents = requiredDocumentRepository.findByService(service);

        List<String> errors = new ArrayList<>();
        Map<String, String> clean = validateFields(service.getName(), fields, errors);
        Map<RequiredDocument, MultipartFile> uploads = validateFiles(documents, files, true, errors);
        if ("ID Card".equals(service.getName()) && LOST_CARD.equals(clean.get("requestType"))
                && uploads.keySet().stream().noneMatch(d -> FIR_DOCUMENT.equals(d.getDocumentName()))) {
            errors.add("An FIR copy is required to replace a lost ID card.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Application application = new Application();
        application.setStudent(student);
        application.setService(service);
        application.setFormData(toJson(clean));
        // A service with a fee waits for the online payment before it is submitted
        // Then the teacher named in the form approves by email before staff see it
        Application saved = service.getFeeAmount() > 0
                ? workflowService.saveDraft(application)
                : service.isRequiresSupervisorApproval()
                        ? supervisorApprovalService.start(application)
                        : workflowService.submit(application);
        saveFiles(saved, uploads);
        return saved;
    }

    @Transactional
    public Application resubmit(User student, Long applicationId, String note,
                                Function<String, MultipartFile> files) {
        Application app = applicationRepository.findById(applicationId)
                .filter(a -> a.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (app.getStatus() != ApplicationStatus.AWAITING_CORRECTION) {
            throw new IllegalStateException("This application is not waiting for a correction");
        }
        List<String> errors = new ArrayList<>();
        // Replacement files are optional on resubmission; only the uploaded ones are checked
        Map<RequiredDocument, MultipartFile> uploads =
                validateFiles(requiredDocumentRepository.findByService(app.getService()), files, false, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        saveFiles(app, uploads);
        return workflowService.resubmit(applicationId, student, note);
    }

    private Map<String, String> validateFields(String serviceName, Map<String, String> fields, List<String> errors) {
        Map<String, String> clean = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                clean.put(key, value.trim());
            }
        });
        for (String required : requiredFields(serviceName)) {
            if (!clean.containsKey(required)) {
                errors.add(label(required) + " is required.");
            }
        }
        for (String numeric : NUMBER_FIELDS) {
            if (clean.containsKey(numeric)) {
                try {
                    if (Double.parseDouble(clean.get(numeric)) <= 0) {
                        errors.add(label(numeric) + " must be greater than zero.");
                    }
                } catch (NumberFormatException e) {
                    errors.add(label(numeric) + " must be a number.");
                }
            }
        }
        String teacherEmail = clean.get("teacherEmail");
        if (teacherEmail != null && !teacherEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.add("Teacher's email ID is not a valid email address.");
        }
        if ("Details Correction".equals(serviceName) && clean.containsKey("field")
                && !CORRECTABLE_FIELDS.contains(clean.get("field"))) {
            errors.add("This field cannot be corrected through Docket.");
        }
        return clean;
    }

    private Map<RequiredDocument, MultipartFile> validateFiles(List<RequiredDocument> documents,
                                                               Function<String, MultipartFile> files,
                                                               boolean enforceMandatory, List<String> errors) {
        Map<RequiredDocument, MultipartFile> uploads = new LinkedHashMap<>();
        for (RequiredDocument doc : documents) {
            MultipartFile file = files.apply("doc_" + doc.getId());
            if (file != null && !file.isEmpty()) {
                String error = fileStorageService.validate(file, doc.getDocumentName());
                if (error != null) {
                    errors.add(error);
                } else {
                    uploads.put(doc, file);
                }
            } else if (enforceMandatory && doc.isMandatory()) {
                errors.add(doc.getDocumentName() + " is required.");
            }
        }
        return uploads;
    }

    private void saveFiles(Application application, Map<RequiredDocument, MultipartFile> uploads) {
        uploads.forEach((doc, file) -> {
            ApplicationDocument appDoc = new ApplicationDocument();
            appDoc.setApplication(application);
            appDoc.setRequiredDocument(doc);
            appDoc.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : doc.getDocumentName());
            try {
                appDoc.setData(file.getBytes());   // the file itself is stored in the database
            } catch (IOException e) {
                throw new UncheckedIOException("Could not store " + doc.getDocumentName(), e);
            }
            appDoc.setFileType(file.getContentType());
            appDoc.setFileSize(file.getSize());
            applicationDocumentRepository.save(appDoc);
        });
    }

    private String toJson(Map<String, String> data) {
        String json = objectMapper.writeValueAsString(data);
        if (json.length() > MAX_FORM_DATA) {
            throw new ValidationException(List.of("The form text is too long. Please shorten your answers."));
        }
        return json;
    }

    // "familyIncome" -> "Family income"
    private static String label(String key) {
        String spaced = key.replaceAll("([A-Z])", " $1").toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** Thrown with every problem found, so the form can list them all at once. */
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super(String.join(" ", errors));
            this.errors = errors;
        }

        public List<String> getErrors() { return errors; }
    }
}
