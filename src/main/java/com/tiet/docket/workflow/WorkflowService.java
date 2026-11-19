package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.FeePayment;
import com.tiet.docket.model.PaymentRecord;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.SupervisorApproval;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.FeePaymentRepository;
import com.tiet.docket.repository.PaymentRecordRepository;
import com.tiet.docket.repository.StageRepository;
import com.tiet.docket.repository.SupervisorApprovalRepository;
import com.tiet.docket.repository.UserRepository;
import org.springframework.stereotype.Service;   // Spring's @Service, not our model.Service
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class WorkflowService {

    private final ApplicationRepository applicationRepository;
    private final StageRepository stageRepository;
    private final DecisionHistoryRepository decisionHistoryRepository;
    private final NotificationService notificationService;
    private final PaymentRecordRepository paymentRecordRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final FeePaymentRepository feePaymentRepository;
    private final SupervisorApprovalRepository supervisorApprovalRepository;

    // Repositories and notification service injected via constructor.
    // The six public method signatures are NOT changed.
    public WorkflowService(ApplicationRepository applicationRepository,
                           StageRepository stageRepository,
                           DecisionHistoryRepository decisionHistoryRepository,
                           NotificationService notificationService,
                           PaymentRecordRepository paymentRecordRepository,
                           UserRepository userRepository,
                           AuditService auditService,
                           FeePaymentRepository feePaymentRepository,
                           SupervisorApprovalRepository supervisorApprovalRepository) {
        this.applicationRepository = applicationRepository;
        this.stageRepository = stageRepository;
        this.decisionHistoryRepository = decisionHistoryRepository;
        this.notificationService = notificationService;
        this.paymentRecordRepository = paymentRecordRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.feePaymentRepository = feePaymentRepository;
        this.supervisorApprovalRepository = supervisorApprovalRepository;
    }

    // ---------------------------------------------------------------
    // HOLD: submitted by the student, but waiting for the teacher's email approval
    // before it reaches the first stage (SupervisorApprovalService releases it).
    // ---------------------------------------------------------------
    @Transactional
    public Application holdForSupervisor(Application application) {
        application.setCurrentStage(null);
        application.setStatus(ApplicationStatus.SUPERVISOR_PENDING);
        Application saved = applicationRepository.save(application);
        saved.setReferenceNumber(String.format("DKT-%d-%04d", LocalDate.now().getYear(), saved.getId()));
        saved = applicationRepository.save(saved);
        recordDecision(saved, null, saved.getStudent(), DecisionType.SUBMITTED, null);
        return saved;
    }

    // ---------------------------------------------------------------
    // DRAFT: a service with a fee is saved first and submitted only after payment.
    // ---------------------------------------------------------------
    @Transactional
    public Application saveDraft(Application application) {
        application.setCurrentStage(null);
        application.setStatus(ApplicationStatus.PAYMENT_PENDING);
        Application saved = applicationRepository.save(application);
        saved.setReferenceNumber(String.format("DKT-%d-%04d", LocalDate.now().getYear(), saved.getId()));
        saved = applicationRepository.save(saved);

        final Application draft = saved;
        sendSafely(() -> notificationService.notifyUser(draft.getStudent(), draft,
                "Application " + draft.getReferenceNumber() + " is saved. Pay the fee of Rs. "
                        + draft.getService().getFeeAmount() + " to submit it."));
        return saved;
    }

    // ---------------------------------------------------------------
    // SUBMIT: a student submits; the application goes to step 1.
    // ---------------------------------------------------------------
    @Transactional
    public Application submit(Application application) {
        List<Stage> stages =
                stageRepository.findByServiceOrderByStepOrderAsc(application.getService());
        if (stages.isEmpty()) {
            throw new IllegalStateException("No stages are configured for this service");
        }
        boolean releasedBySupervisor = application.getStatus() == ApplicationStatus.SUPERVISOR_PENDING;
        if (application.getStatus() != null && application.getStatus() != ApplicationStatus.PAYMENT_PENDING
                && !releasedBySupervisor && application.getId() != null) {
            throw new IllegalStateException("This application has already been submitted");
        }
        application.setCurrentStage(stages.get(0));
        // Verification services (UC-03, UC-05) start as "Under review" once validation passed
        application.setStatus(application.getService().getFinalStatus() == ApplicationStatus.VERIFIED
                ? ApplicationStatus.UNDER_REVIEW : ApplicationStatus.SUBMITTED);
        Application saved = applicationRepository.save(application);

        // Readable reference number, e.g. DKT-2026-0001 (needs the generated id)
        saved.setReferenceNumber(
                String.format("DKT-%d-%04d", LocalDate.now().getYear(), saved.getId()));
        saved = applicationRepository.save(saved);
        if (!releasedBySupervisor) {   // the submission was already recorded when it was held
            recordDecision(saved, saved.getCurrentStage(), saved.getStudent(), DecisionType.SUBMITTED, null);
        }

        // ---- Notifications ----
        // Java lambdas require captured variables to be final (or effectively final).
        // Because 'saved' was reassigned above, we copy it to a new final variable.
        final Application finalSaved = saved;
        final Stage finalFirstStage = finalSaved.getCurrentStage();

        // 1. Tell the student their application was received.
        String ref = finalSaved.getReferenceNumber();
        String serviceName = finalSaved.getService().getName();
        sendSafely(() ->
            notificationService.notifyUser(
                finalSaved.getStudent(), finalSaved,
                "Your application " + ref + " for " + serviceName + " was submitted.")
        );

        // 2. Tell all officers of the first stage that a new application is waiting.
        sendSafely(() ->
            notificationService.notifyRole(
                finalFirstStage.getHandlerRole(),
                finalFirstStage.getDepartment(),
                finalSaved,
                "New application " + ref + " is waiting for your review.")
        );

        return finalSaved;
    }

    // ---------------------------------------------------------------
    // APPROVE: record the decision, then move to the next stage or finish.
    // ---------------------------------------------------------------
    @Transactional
    public Application approve(Long applicationId, User officer) {
        return approve(applicationId, officer, true);
    }

    // forward = false skips an optional next stage (e.g. scholarship does not need Finance)
    @Transactional
    public Application approve(Long applicationId, User officer, boolean forward) {
        Application app = findApplication(applicationId);
        checkOfficerCanAct(app, officer);

        Stage current = app.getCurrentStage();

        // The teacher's email approval must be on record before any officer approves
        if (app.getService().isRequiresSupervisorApproval() && !supervisorApprovalRepository
                .existsByApplicationAndStatus(app, SupervisorApproval.Status.APPROVED)) {
            throw new IllegalStateException("The supervising teacher has not approved this application yet");
        }

        // Task 1D: Guard that accounts officer records payment before processing
        if (current.getHandlerRole() == Role.ACCOUNTS_OFFICER
                && !paymentRecordRepository.existsByApplication(app)) {
            throw new IllegalStateException("Record the payment reference and date before processing");
        }

        Optional<Stage> next = nextStage(current, forward);
        // Finance and verification officers record "Verified" rather than "Approved" (SRS 1.3)
        DecisionType passed = current.getHandlerRole() == Role.FINANCE_OFFICER
                || app.getService().getFinalStatus() == ApplicationStatus.VERIFIED
                ? DecisionType.VERIFIED : DecisionType.APPROVED;

        if (next.isPresent()) {
            // Moving to the next stage.
            recordDecision(app, current, officer, passed, null);
            app.setCurrentStage(next.get());
            app.setStatus(ApplicationStatus.UNDER_REVIEW);
            Application saved = applicationRepository.save(app);

            // ---- Notifications ----
            String ref = saved.getReferenceNumber();
            Stage nextStage = next.get();
            // Tell the student which stage they moved to.
            sendSafely(() ->
                notificationService.notifyUser(
                    saved.getStudent(), saved,
                    "Application " + ref + " moved to " + nextStage.getName() + ".")
            );
            // Tell the officers of the next stage.
            sendSafely(() ->
                notificationService.notifyRole(
                    nextStage.getHandlerRole(),
                    nextStage.getDepartment(),
                    saved,
                    "New application " + ref + " is waiting for your review.")
            );
            return saved;

        } else {
            // Last stage: the application is finished.

            // The fee is checked by Finance in parallel; no final decision before it is confirmed
            if (app.getService().getFeeAmount() > 0 && !feePaymentRepository
                    .existsByApplicationAndVerification(app, FeePayment.Verification.CONFIRMED)) {
                throw new IllegalStateException(
                        "The Finance Officer has not confirmed the fee payment yet. Approve after it is confirmed.");
            }

            // Task 2D: If this service updates the student record (e.g. Details Correction)
            if (app.getService().isUpdatesStudentRecord()) {
                applyStudentRecordChange(app, officer);
                recordDecision(app, current, officer, DecisionType.APPROVED, null);
                app.setCurrentStage(null);
                app.setStatus(ApplicationStatus.CLOSED);
                Application saved = applicationRepository.save(app);

                // ---- Notifications ----
                String ref = saved.getReferenceNumber();
                sendSafely(() ->
                    notificationService.notifyUser(
                        saved.getStudent(), saved,
                        "Application " + ref + " was approved and your record has been updated.")
                );
                return saved;
            }

            boolean paymentStage = current.getHandlerRole() == Role.ACCOUNTS_OFFICER;
            ApplicationStatus finalStatus = paymentStage
                    ? ApplicationStatus.PROCESSED : app.getService().getFinalStatus();
            recordDecision(app, current, officer,
                    paymentStage ? DecisionType.PROCESSED : passed, null);
            app.setCurrentStage(null);
            app.setStatus(finalStatus);
            Application saved = applicationRepository.save(app);

            // ---- Notifications ----
            String ref = saved.getReferenceNumber();
            // "processed" for ACCOUNTS_OFFICER final step, "approved" otherwise.
            String outcome = finalStatus.name().toLowerCase();
            sendSafely(() ->
                notificationService.notifyUser(
                    saved.getStudent(), saved,
                    "Application " + ref + " has been " + outcome + ".")
            );
            return saved;
        }
    }

    // ---------------------------------------------------------------
    // RECORD PAYMENT AND PROCESS: Accounts Officer saves payment details
    // and completes the stage, marking the application as PROCESSED.
    // ---------------------------------------------------------------
    @Transactional
    public Application recordPaymentAndProcess(Long applicationId, User officer,
                                               String paymentReference, LocalDate paymentDate) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required and cannot be blank");
        }
        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date is required and cannot be null");
        }

        Application app = findApplication(applicationId);
        checkOfficerCanAct(app, officer);

        Stage current = app.getCurrentStage();
        if (current.getHandlerRole() != Role.ACCOUNTS_OFFICER) {
            throw new IllegalStateException("This application is not at the payment stage");
        }

        // Save the payment record
        PaymentRecord record = new PaymentRecord();
        record.setApplication(app);
        record.setRecordedBy(officer);
        record.setPaymentReference(paymentReference.trim());
        record.setPaymentDate(paymentDate);
        paymentRecordRepository.save(record);

        // Complete the stage (same as existing approve logic for accounts officer)
        recordDecision(app, current, officer, DecisionType.PROCESSED, null);
        app.setCurrentStage(null);
        app.setStatus(ApplicationStatus.PROCESSED);
        Application saved = applicationRepository.save(app);

        // ---- Notifications ----
        String ref = saved.getReferenceNumber();
        sendSafely(() ->
            notificationService.notifyUser(
                saved.getStudent(), saved,
                "Application " + ref + " has been processed.")
        );
        return saved;
    }

    // ---------------------------------------------------------------
    // REJECT: a reason is mandatory.
    // ---------------------------------------------------------------
    @Transactional
    public Application reject(Long applicationId, User officer, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reject an application");
        }
        Application app = findApplication(applicationId);
        checkOfficerCanAct(app, officer);

        recordDecision(app, app.getCurrentStage(), officer, DecisionType.REJECTED, reason);
        app.setCurrentStage(null);
        app.setStatus(ApplicationStatus.REJECTED);
        Application saved = applicationRepository.save(app);

        // ---- Notification ----
        String ref = saved.getReferenceNumber();
        sendSafely(() ->
            notificationService.notifyUser(
                saved.getStudent(), saved,
                "Application " + ref + " was rejected. Reason: " + reason)
        );
        return saved;
    }

    // ---------------------------------------------------------------
    // REQUEST CORRECTION: goes back to the student; the stage stays the same.
    // ---------------------------------------------------------------
    @Transactional
    public Application requestCorrection(Long applicationId, User officer, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Please describe what the student must correct");
        }
        Application app = findApplication(applicationId);
        checkOfficerCanAct(app, officer);

        // A Finance return is recorded as "Issue found" (unpaid fee / wrong bills)
        recordDecision(app, app.getCurrentStage(), officer,
                officer.getRole() == Role.FINANCE_OFFICER
                        ? DecisionType.ISSUE_FOUND : DecisionType.CORRECTION_REQUESTED, message);
        app.setStatus(ApplicationStatus.AWAITING_CORRECTION);
        Application saved = applicationRepository.save(app);

        // ---- Notification ----
        String ref = saved.getReferenceNumber();
        sendSafely(() ->
            notificationService.notifyUser(
                saved.getStudent(), saved,
                "Application " + ref + " needs correction: " + message)
        );
        return saved;
    }

    // ---------------------------------------------------------------
    // RESUBMIT: the student fixes it; review resumes at the SAME stage.
    // ---------------------------------------------------------------
    @Transactional
    public Application resubmit(Long applicationId, User student) {
        return resubmit(applicationId, student, null);
    }

    // note: what the student changed, e.g. the fee payment reference (FR12)
    @Transactional
    public Application resubmit(Long applicationId, User student, String note) {
        Application app = findApplication(applicationId);

        if (!app.getStudent().getId().equals(student.getId())) {
            throw new IllegalStateException("You can only resubmit your own application");
        }
        if (app.getStatus() != ApplicationStatus.AWAITING_CORRECTION) {
            throw new IllegalStateException("This application is not waiting for a correction");
        }
        app.setStatus(ApplicationStatus.UNDER_REVIEW);
        Application saved = applicationRepository.save(app);
        recordDecision(saved, saved.getCurrentStage(), student, DecisionType.RESUBMITTED,
                note == null || note.isBlank() ? null : note.trim());

        // ---- Notification ----
        // Tell the current stage's officers that the student has resubmitted.
        Stage currentStage = saved.getCurrentStage();
        String ref = saved.getReferenceNumber();
        sendSafely(() ->
            notificationService.notifyRole(
                currentStage.getHandlerRole(),
                currentStage.getDepartment(),
                saved,
                "Student resubmitted application " + ref + ". Please review.")
        );
        return saved;
    }

    // ---------------------------------------------------------------
    // STAFF QUEUE: applications waiting at a stage handled by this role.
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<Application> getPendingFor(User officer) {
        return applicationRepository.findByCurrentStage_HandlerRoleAndStatusIn(
                        officer.getRole(),
                        List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW))
                .stream()
                .filter(app -> inOfficerDepartment(app.getCurrentStage(), officer))
                .toList();
    }

    // The stage after 'current', skipping an optional stage when the officer does not forward.
    @Transactional(readOnly = true)
    public Optional<Stage> nextStage(Stage current, boolean forward) {
        return stageRepository.findByServiceOrderByStepOrderAsc(current.getService()).stream()
                .filter(s -> s.getStepOrder() > current.getStepOrder())
                .filter(s -> forward || !s.isOptional())
                .findFirst();
    }

    // True if this officer may act on the application right now (used by the UI too).
    public boolean canAct(Application app, User officer) {
        try {
            checkOfficerCanAct(app, officer);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Application findApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
    }

    // The officer may act only if the application is waiting for staff
    // and their role (and department, if the stage names one) matches the stage.
    private void checkOfficerCanAct(Application app, User officer) {
        Stage stage = app.getCurrentStage();
        boolean waitingForStaff = app.getStatus() == ApplicationStatus.SUBMITTED
                || app.getStatus() == ApplicationStatus.UNDER_REVIEW;

        if (stage == null || !waitingForStaff) {
            throw new IllegalStateException("This application is not waiting for staff action");
        }
        if (officer.getRole() != stage.getHandlerRole()) {
            throw new IllegalStateException("Your role cannot act on this stage");
        }
        if (!inOfficerDepartment(stage, officer)) {
            throw new IllegalStateException("This stage belongs to another department");
        }
    }

    // A stage without a department is open to every officer of its role.
    private boolean inOfficerDepartment(Stage stage, User officer) {
        String dept = stage.getDepartment();
        return dept == null || dept.isBlank() || dept.equalsIgnoreCase(officer.getDepartment());
    }

    // Every decision is saved to the audit trail (never edited or deleted).
    private void recordDecision(Application app, Stage stage, User officer,
                                DecisionType decision, String reason) {
        DecisionHistory history = new DecisionHistory();
        history.setApplication(app);
        history.setStage(stage);
        history.setOfficer(officer);
        history.setDecision(decision);
        history.setReason(reason);
        decisionHistoryRepository.save(history);
    }

    /**
     * Runs a notification action and swallows any exception that is NOT a
     * database error (i.e. not a RuntimeException from the persistence layer).
     *
     * Rule: a notification failure must never break a workflow action.
     * We still let DataAccessException (and similar JPA errors) propagate
     * because those would roll back the whole transaction anyway.
     *
     * For simplicity we catch Exception here; in a production system you
     * would catch more specific non-fatal exceptions.
     */
    private void sendSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            // Log and continue; the workflow action already succeeded.
            System.err.println("[NotificationService] Notification failed (ignored): "
                    + e.getMessage());
        }
    }

    /**
     * Parses the application's JSON formData and updates the student's User record.
     * Whitelist allowed fields: "name", "phone", "course", "rollNumber".
     * Throws IllegalStateException if formData is missing, invalid, or field is not allowed.
     */
    private void applyStudentRecordChange(Application app, User officer) {
        String formData = app.getFormData();
        if (formData == null || formData.isBlank()) {
            throw new IllegalStateException("Form data is missing or empty");
        }

        JsonNode root;
        try {
            ObjectMapper mapper = new ObjectMapper();
            root = mapper.readTree(formData);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON format in form data: " + e.getMessage(), e);
        }

        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Form data must be a valid JSON object");
        }

        JsonNode fieldNode = root.get("field");
        JsonNode valueNode = root.get("newValue");

        if (fieldNode == null || fieldNode.isNull()) {
            throw new IllegalStateException("Field name is missing in form data");
        }
        String field = fieldNode.asText();
        if (field.isBlank()) {
            throw new IllegalStateException("Field name cannot be blank");
        }

        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalStateException("New value is missing in form data");
        }
        String newValue = valueNode.asText();
        if (newValue.isBlank()) {
            throw new IllegalStateException("New value cannot be blank");
        }

        User student = app.getStudent();
        String oldValue = switch (field) {
            case "name" -> student.getName();
            case "phone" -> student.getPhone();
            case "course" -> student.getCourse();
            case "rollNumber" -> student.getRollNumber();
            default -> null;
        };
        switch (field) {
            case "name" -> student.setName(newValue);
            case "phone" -> student.setPhone(newValue);
            case "course" -> student.setCourse(newValue);
            case "rollNumber" -> student.setRollNumber(newValue);
            default -> throw new IllegalStateException("Field '" + field + "' is not allowed to be updated");
        }
        userRepository.save(student);
        auditService.log(officer, "STUDENT_RECORD_UPDATED",
                student.getEmail() + ": " + field + " changed from '" + oldValue + "' to '" + newValue
                        + "' (application " + app.getReferenceNumber() + ")");
    }
}