package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.SupervisorApproval;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.SupervisorApprovalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Supervisor approval by email: the teacher the student names gets a one-time
 * link, approves or rejects without an account, and only an approved
 * application is released to the first staff stage (the HOD for reimbursements).
 */
@Service
public class SupervisorApprovalService {

    private static final int LINK_VALID_DAYS = 7;

    private final SupervisorApprovalRepository approvalRepository;
    private final ApplicationRepository applicationRepository;
    private final DecisionHistoryRepository historyRepository;
    private final WorkflowService workflowService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final String publicUrl;
    private final String allowedDomain;
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SupervisorApprovalService(SupervisorApprovalRepository approvalRepository,
                                     ApplicationRepository applicationRepository,
                                     DecisionHistoryRepository historyRepository,
                                     WorkflowService workflowService,
                                     NotificationService notificationService,
                                     EmailService emailService,
                                     @Value("${docket.public-url}") String publicUrl,
                                     @Value("${docket.supervisor.allowed-email-domain:}") String allowedDomain) {
        this.approvalRepository = approvalRepository;
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.allowedDomain = allowedDomain.trim().toLowerCase();
    }

    /** Holds a just-submitted application and emails the teacher named in its form. */
    @Transactional
    public Application start(Application application) {
        JsonNode form = objectMapper.readTree(application.getFormData() == null ? "{}" : application.getFormData());
        String name = text(form, "teacherName");
        String department = text(form, "teacherDepartment");
        String email = text(form, "teacherEmail").toLowerCase();
        if (name.isEmpty() || department.isEmpty() || email.isEmpty()) {
            throw new IllegalArgumentException("Teacher name, department and email are required");
        }
        if (email.equalsIgnoreCase(application.getStudent().getEmail())) {
            throw new IllegalArgumentException("Enter your teacher's email address, not your own");
        }
        if (!allowedDomain.isEmpty() && !email.endsWith("@" + allowedDomain)) {
            throw new IllegalArgumentException("The teacher's email must be a @" + allowedDomain + " address");
        }

        Application held = workflowService.holdForSupervisor(application);
        SupervisorApproval approval = new SupervisorApproval();
        approval.setApplication(held);
        approval.setTeacherName(name);
        approval.setTeacherDepartment(department);
        approval.setTeacherEmail(email);
        sendLink(approval);

        notificationService.notifyUser(held.getStudent(), held, "Application " + held.getReferenceNumber()
                + " was sent to " + name + " (" + email + ") for approval by email.");
        return held;
    }

    /** New link (the old one stops working), e.g. when the teacher lost the email. */
    @Transactional
    public SupervisorApproval resend(Long applicationId, User student) {
        SupervisorApproval approval = approvalRepository.findByApplication(
                        applicationRepository.findById(applicationId)
                                .filter(a -> a.getStudent().getId().equals(student.getId()))
                                .orElseThrow(() -> new IllegalArgumentException("Application not found")))
                .orElseThrow(() -> new IllegalStateException("This application does not need a teacher's approval"));
        if (approval.getStatus() != SupervisorApproval.Status.PENDING) {
            throw new IllegalStateException("The teacher has already responded");
        }
        sendLink(approval);
        return approval;
    }

    /** The approval behind a link, if the link is genuine and still valid. */
    @Transactional(readOnly = true)
    public SupervisorApproval byToken(String token) {
        SupervisorApproval approval = approvalRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new IllegalArgumentException("This approval link is not valid"));
        if (approval.getStatus() == SupervisorApproval.Status.PENDING
                && approval.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This approval link has expired. Ask the student to resend it.");
        }
        return approval;
    }

    @Transactional
    public SupervisorApproval decide(String token, boolean approve, String remark) {
        SupervisorApproval approval = byToken(token);
        if (approval.getStatus() != SupervisorApproval.Status.PENDING) {
            throw new IllegalStateException("You have already responded to this request");
        }
        String note = remark == null || remark.isBlank() ? null : remark.trim();
        if (!approve && note == null) {
            throw new IllegalArgumentException("Please give a reason for rejecting");
        }
        Application app = approval.getApplication();
        approval.setStatus(approve ? SupervisorApproval.Status.APPROVED : SupervisorApproval.Status.REJECTED);
        approval.setRemark(note);
        approval.setDecidedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        DecisionHistory h = new DecisionHistory();
        h.setApplication(app);
        h.setExternalActor(approval.getTeacherName() + " (" + approval.getTeacherEmail() + ")");
        h.setDecision(approve ? DecisionType.APPROVED : DecisionType.REJECTED);
        h.setReason(approve ? (note == null ? "Approved by the supervising teacher" : note) : note);
        historyRepository.save(h);

        if (approve) {
            // Now the application reaches the first staff stage (the HOD) and they are notified
            workflowService.submit(app);
            notificationService.notifyUser(app.getStudent(), app, approval.getTeacherName()
                    + " approved " + app.getReferenceNumber() + ". It has been sent to the next stage.");
        } else {
            app.setStatus(ApplicationStatus.REJECTED);
            applicationRepository.save(app);
            notificationService.notifyUser(app.getStudent(), app, approval.getTeacherName()
                    + " rejected " + app.getReferenceNumber() + ". Reason: " + note);
        }
        return approval;
    }

    // Creates a fresh token, keeps only its hash, and emails the link
    private void sendLink(SupervisorApproval approval) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        approval.setTokenHash(hash(token));
        approval.setExpiresAt(LocalDateTime.now().plusDays(LINK_VALID_DAYS));
        approval.setEmailSentAt(LocalDateTime.now());
        approvalRepository.save(approval);

        Application app = approval.getApplication();
        User s = app.getStudent();
        String link = publicUrl + "/supervisor/" + token;
        emailService.send(approval.getTeacherEmail(),
                "Docket: approval needed for " + app.getService().getName() + " " + app.getReferenceNumber(),
                "Dear " + approval.getTeacherName() + ",\n\n"
                        + s.getName() + (s.getRollNumber() == null ? "" : " (roll " + s.getRollNumber() + ")")
                        + " has named you as the supervising teacher for a " + app.getService().getName()
                        + " application (" + app.getReferenceNumber() + ") on Docket, TIET Patiala.\n\n"
                        + "Please review the details and approve or reject it here (no login needed):\n"
                        + link + "\n\n"
                        + "This link works once and expires in " + LINK_VALID_DAYS + " days. After your approval the "
                        + "application goes to the Head of Department.\n\n"
                        + "If you do not know this student or this request, please choose Reject.\n\n"
                        + "Docket - Digital Campus Administration Portal");
    }

    private static String text(JsonNode form, String key) {
        JsonNode node = form.get(key);
        return node == null || node.isNull() ? "" : node.asText().trim();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
