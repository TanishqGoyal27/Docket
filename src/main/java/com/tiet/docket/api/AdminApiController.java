package com.tiet.docket.api;

import com.tiet.docket.admin.AdminService;
import com.tiet.docket.api.ApiDtos.ApplicationSummary;
import com.tiet.docket.api.ApiDtos.AuditDto;
import com.tiet.docket.api.ApiDtos.DocumentRequest;
import com.tiet.docket.api.ApiDtos.ServiceDto;
import com.tiet.docket.api.ApiDtos.ServiceRequest;
import com.tiet.docket.api.ApiDtos.StageDto;
import com.tiet.docket.api.ApiDtos.StageRequest;
import com.tiet.docket.api.ApiDtos.UserDto;
import com.tiet.docket.api.ApiDtos.UserRequest;
import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.workflow.AuditService;
import com.tiet.docket.workflow.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrator console (UC-09): users, roles, services, stages, documents,
 * plus read-only views of every application and the change log. Every change
 * is written to the audit log (FR40).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService adminService;
    private final ApplicationViewService viewService;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;
    private final EmailService emailService;

    public AdminApiController(AdminService adminService, ApplicationViewService viewService,
                              ApplicationRepository applicationRepository, AuditService auditService,
                              EmailService emailService) {
        this.adminService = adminService;
        this.viewService = viewService;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Application> apps = applicationRepository.findAll();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), apps.stream().filter(a -> a.getStatus() == status).count());
        }
        return Map.of("users", adminService.getUserCount(),
                "services", adminService.getServiceCount(),
                "stages", adminService.getStageCount(),
                "applications", (long) apps.size(),
                "byStatus", byStatus);
    }

    // ---------- users ----------

    @GetMapping("/users")
    public List<UserDto> users() {
        return adminService.getAllUsers().stream().map(UserDto::of).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@RequestBody UserRequest r, Authentication auth) {
        User created = adminService.createUser(r.name(), r.email(), r.password(), role(r.role()),
                r.department(), r.rollNumber(), r.course(), r.phone());
        log(auth, "USER_CREATED", created.getEmail() + " as " + created.getRole());
        return UserDto.of(created);
    }

    @PutMapping("/users/{id}")
    public UserDto updateUser(@PathVariable Long id, @RequestBody UserRequest r, Authentication auth) {
        User admin = viewService.currentUser(auth);
        User updated = adminService.updateUser(id, admin.getId(), r.name(), role(r.role()), r.department(),
                r.phone(), r.course(), r.rollNumber(), r.active() == null || r.active(), r.resetPassword());
        boolean reset = r.resetPassword() != null && !r.resetPassword().isBlank();
        log(auth, "USER_UPDATED", updated.getEmail() + ": role " + updated.getRole()
                + ", active " + updated.isActive() + (reset ? ", password reset" : ""));
        return UserDto.of(updated);
    }

    @PostMapping("/users/{id}/unlock")
    public UserDto unlock(@PathVariable Long id, Authentication auth) {
        User user = adminService.unlockUser(id);
        log(auth, "USER_UNLOCKED", user.getEmail());
        return UserDto.of(user);
    }

    // ---------- services, stages, documents ----------

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return adminService.getAllServices().stream().map(this::serviceDto).toList();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto createService(@RequestBody ServiceRequest r, Authentication auth) {
        ApplicationStatus outcome = r.finalStatus() == null ? ApplicationStatus.APPROVED
                : ApplicationStatus.valueOf(r.finalStatus());
        Service s = adminService.createService(r.name(), r.description(), outcome,
                Boolean.TRUE.equals(r.requiresVisit()));
        log(auth, "SERVICE_CREATED", s.getName() + " (outcome " + outcome + ")");
        return serviceDto(s);
    }

    @PutMapping("/services/{id}")
    public ServiceDto updateService(@PathVariable Long id, @RequestBody ServiceRequest r, Authentication auth) {
        Service s = adminService.updateService(id, r.description(), Boolean.TRUE.equals(r.active()));
        if (r.feeAmount() != null) {
            s = adminService.setServiceFee(id, r.feeAmount());
        }
        log(auth, "SERVICE_UPDATED", s.getName() + ": active " + s.isActive() + ", fee Rs. " + s.getFeeAmount());
        return serviceDto(s);
    }

    @PostMapping("/services/{id}/stages")
    @ResponseStatus(HttpStatus.CREATED)
    public StageDto addStage(@PathVariable Long id, @RequestBody StageRequest r, Authentication auth) {
        Stage stage = adminService.addStage(id, r.name(), role(r.handlerRole()), r.department());
        if (Boolean.TRUE.equals(r.optional())) {
            stage = adminService.setStageOptional(stage.getId(), true);
        }
        log(auth, "STAGE_ADDED", stageText(stage));
        return StageDto.of(stage);
    }

    @PutMapping("/stages/{id}")
    public StageDto updateStage(@PathVariable Long id, @RequestBody StageRequest r, Authentication auth) {
        adminService.updateStage(id, r.name(), role(r.handlerRole()), r.department());
        Stage stage = adminService.setStageOptional(id, Boolean.TRUE.equals(r.optional()));
        log(auth, "STAGE_UPDATED", stageText(stage));
        return StageDto.of(stage);
    }

    @PostMapping("/services/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.RequiredDocDto addDocument(@PathVariable Long id, @RequestBody DocumentRequest r,
                                              Authentication auth) {
        RequiredDocument doc = adminService.addRequiredDocument(id, r.documentName(),
                r.mandatory() == null || r.mandatory());
        log(auth, "DOCUMENT_ADDED", doc.getService().getName() + ": " + doc.getDocumentName()
                + (doc.isMandatory() ? " (mandatory)" : " (optional)"));
        return ApiDtos.RequiredDocDto.of(doc);
    }

    @PostMapping("/documents/{id}/toggle")
    public ApiDtos.RequiredDocDto toggleDocument(@PathVariable Long id, Authentication auth) {
        RequiredDocument doc = adminService.toggleRequiredDocumentMandatory(id);
        log(auth, "DOCUMENT_UPDATED", doc.getService().getName() + ": " + doc.getDocumentName()
                + (doc.isMandatory() ? " now mandatory" : " now optional"));
        return ApiDtos.RequiredDocDto.of(doc);
    }

    // ---------- monitoring ----------

    @GetMapping("/applications")
    public List<ApplicationSummary> applications() {
        return applicationRepository.findAllByOrderBySubmittedAtDesc().stream().map(ApplicationSummary::of).toList();
    }

    // Every email Docket sent, or saved without sending when no mail server is configured
    @GetMapping("/emails")
    public Map<String, Object> emails() {
        return Map.of("smtpConfigured", emailService.smtpConfigured(),
                "emails", emailService.outbox().stream().map(ApiDtos.EmailDto::of).toList());
    }

    @GetMapping("/audit")
    public List<AuditDto> audit() {
        return auditService.getAll().stream().map(AuditDto::of).toList();
    }

    // ---------- helpers ----------

    private ServiceDto serviceDto(Service s) {
        return ServiceDto.of(s, adminService.getStagesForService(s.getId()),
                adminService.getDocumentsForService(s.getId()));
    }

    private static String stageText(Stage s) {
        return s.getService().getName() + " step " + s.getStepOrder() + ": " + s.getName()
                + " by " + s.getHandlerRole() + (s.isOptional() ? " (optional)" : "");
    }

    private static Role role(String name) {
        if (name == null || name.isBlank()) {
            return null;   // AdminService reports "Role is required"
        }
        return Role.valueOf(name);
    }

    private void log(Authentication auth, String action, String details) {
        auditService.log(viewService.currentUser(auth), action, details);
    }
}
