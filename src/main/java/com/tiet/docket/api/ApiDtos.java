package com.tiet.docket.api;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.Appointment;
import com.tiet.docket.model.AuditLog;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.EmailLog;
import com.tiet.docket.model.SupervisorApproval;
import com.tiet.docket.model.FeePayment;
import com.tiet.docket.model.Notification;
import com.tiet.docket.model.PaymentRecord;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.User;
import com.tiet.docket.student.SubmissionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JSON shapes sent to and from the React frontend. Entities are never sent
 * directly, so passwords and internal file paths can never leak.
 */
public final class ApiDtos {

    private ApiDtos() {}

    // ---------- responses ----------

    public record UserDto(Long id, String name, String email, String role, String department,
                          String rollNumber, String course, String phone, boolean active, boolean locked) {
        public static UserDto of(User u) {
            boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now());
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getDepartment(),
                    u.getRollNumber(), u.getCourse(), u.getPhone(), u.isActive(), locked);
        }
    }

    public record StageDto(Long id, int stepOrder, String name, String handlerRole, String department,
                           boolean optional) {
        public static StageDto of(Stage s) {
            return new StageDto(s.getId(), s.getStepOrder(), s.getName(), s.getHandlerRole().name(),
                    s.getDepartment(), s.isOptional());
        }
    }

    public record RequiredDocDto(Long id, String documentName, boolean mandatory) {
        public static RequiredDocDto of(RequiredDocument d) {
            return new RequiredDocDto(d.getId(), d.getDocumentName(), d.isMandatory());
        }
    }

    public record ServiceDto(Long id, String name, String description, boolean active, String finalStatus,
                             boolean requiresVisit, boolean updatesStudentRecord, int feeAmount,
                             List<String> requiredFields, List<StageDto> stages, List<RequiredDocDto> documents) {
        public static ServiceDto of(Service s, List<Stage> stages, List<RequiredDocument> docs) {
            return new ServiceDto(s.getId(), s.getName(), s.getDescription(), s.isActive(),
                    s.getFinalStatus().name(), s.isRequiresVisit(), s.isUpdatesStudentRecord(), s.getFeeAmount(),
                    SubmissionService.requiredFields(s.getName()),
                    stages.stream().map(StageDto::of).toList(),
                    docs.stream().map(RequiredDocDto::of).toList());
        }
    }

    public record ApplicationSummary(Long id, String referenceNumber, String serviceName, String studentName,
                                     String status, String currentStage, String currentRole, String department,
                                     LocalDateTime submittedAt, LocalDateTime updatedAt) {
        public static ApplicationSummary of(Application a) {
            Stage st = a.getCurrentStage();
            return new ApplicationSummary(a.getId(), a.getReferenceNumber(), a.getService().getName(),
                    a.getStudent().getName(), a.getStatus().name(),
                    st == null ? null : st.getName(),
                    st == null ? null : st.getHandlerRole().name(),
                    st == null ? null : st.getDepartment(),
                    a.getSubmittedAt(), a.getUpdatedAt());
        }
    }

    public record DocumentDto(Long id, String documentName, String fileName, String fileType, long fileSize,
                              LocalDateTime uploadedAt, boolean feeReceipt, boolean replaced) {
        public static DocumentDto of(ApplicationDocument d) {
            String name = d.isFeeReceipt() ? "Fee receipt (system generated)"
                    : d.getRequiredDocument() == null ? "Attached document" : d.getRequiredDocument().getDocumentName();
            return new DocumentDto(d.getId(), name, d.getFileName(), d.getFileType(), d.getFileSize(),
                    d.getUploadedAt(), d.isFeeReceipt(), d.isReplaced());
        }
    }

    public record HistoryDto(Long id, String decision, String actorName, String actorRole, String stageName,
                             String reason, LocalDateTime at) {
        public static HistoryDto of(DecisionHistory h) {
            boolean byEmail = h.getOfficer() == null;   // the teacher's approval from the email link
            return new HistoryDto(h.getId(), h.getDecision().name(),
                    byEmail ? h.getExternalActor() : h.getOfficer().getName(),
                    byEmail ? "SUPERVISOR" : h.getOfficer().getRole().name(),
                    h.getStage() == null ? null : h.getStage().getName(),
                    h.getReason(), h.getDecidedAt());
        }
    }

    public record PaymentDto(String reference, LocalDate date, String recordedBy) {
        public static PaymentDto of(PaymentRecord p) {
            return new PaymentDto(p.getPaymentReference(), p.getPaymentDate(), p.getRecordedBy().getName());
        }
    }

    public record AppointmentDto(Long id, LocalDateTime slot, String referenceNumber, Long applicationId,
                                 String studentName) {
        public static AppointmentDto of(Appointment a) {
            return new AppointmentDto(a.getId(), a.getSlot(), a.getApplication().getReferenceNumber(),
                    a.getApplication().getId(), a.getApplication().getStudent().getName());
        }
    }

    // Application fee paid online, as Finance sees it in the payment records
    public record FeeDto(Long applicationId, String referenceNumber, String studentName, String serviceName,
                         int amount, String gateway, String orderId, String paymentId, String status,
                         String receiptNumber, LocalDateTime paidAt, String verification, String verifiedBy,
                         LocalDateTime verifiedAt, String remark, String applicationStatus) {
        public static FeeDto of(FeePayment p) {
            Application a = p.getApplication();
            return new FeeDto(a.getId(), a.getReferenceNumber(), a.getStudent().getName(), a.getService().getName(),
                    p.getAmount(), p.getGateway(), p.getOrderId(), p.getPaymentId(), p.getStatus().name(),
                    p.getReceiptNumber(), p.getPaidAt(),
                    p.getVerification() == null ? null : p.getVerification().name(),
                    p.getVerifiedBy() == null ? null : p.getVerifiedBy().getName(),
                    p.getVerifiedAt(), p.getRemark(), a.getStatus().name());
        }
    }

    // The supervising teacher's email approval
    public record SupervisorDto(String teacherName, String teacherDepartment, String teacherEmail, String status,
                                String remark, LocalDateTime emailSentAt, LocalDateTime decidedAt) {
        public static SupervisorDto of(SupervisorApproval s) {
            return new SupervisorDto(s.getTeacherName(), s.getTeacherDepartment(), s.getTeacherEmail(),
                    s.getStatus().name(), s.getRemark(), s.getEmailSentAt(), s.getDecidedAt());
        }
    }

    public record EmailDto(Long id, String recipient, String subject, String body, String status, String error,
                           LocalDateTime createdAt) {
        public static EmailDto of(EmailLog e) {
            return new EmailDto(e.getId(), e.getRecipient(), e.getSubject(), e.getBody(), e.getStatus().name(),
                    e.getError(), e.getCreatedAt());
        }
    }

    // What the teacher sees from the emailed link (no login)
    public record SupervisorView(String referenceNumber, String serviceName, String studentName, String rollNumber,
                                 String course, Map<String, String> formData, List<DocumentDto> documents,
                                 String teacherName, String status, String remark, LocalDateTime decidedAt) {}

    public record SupervisorDecision(Boolean approve, String remark) {}

    // What the viewer may do on the detail page
    public record Actions(boolean canAct, boolean canResubmit, boolean paymentStage, String nextStageName,
                          boolean nextStageOptional, boolean canBookVisit, boolean canPay, boolean canVerifyFee,
                          boolean canResendSupervisor) {}

    public record ApplicationDetail(ApplicationSummary summary, String serviceDescription, UserDto student,
                                    Map<String, String> formData, List<DocumentDto> documents,
                                    List<HistoryDto> history, List<StageDto> stages, PaymentDto payment,
                                    AppointmentDto appointment, String pendingMessage,
                                    List<RequiredDocDto> requiredDocuments, int feeAmount, FeeDto fee,
                                    SupervisorDto supervisor, Actions actions) {}

    public record NotificationDto(Long id, String message, boolean read, LocalDateTime createdAt,
                                  Long applicationId) {
        public static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getMessage(), n.isRead(), n.getCreatedAt(),
                    n.getApplication() == null ? null : n.getApplication().getId());
        }
    }

    public record AuditDto(Long id, String actorName, String action, String details, LocalDateTime at) {
        public static AuditDto of(AuditLog a) {
            return new AuditDto(a.getId(), a.getActor() == null ? "System" : a.getActor().getName(),
                    a.getAction(), a.getDetails(), a.getCreatedAt());
        }
    }

    // ---------- requests ----------

    public record LoginRequest(String email, String password) {}

    public record RegisterRequest(String name, String email, String password, String rollNumber,
                                  String course, String phone) {}

    public record DecisionRequest(String reason, Boolean forward) {}

    public record PaymentRequest(String reference, LocalDate date) {}

    // Gateway response after checkout (Razorpay's razorpay_order_id / _payment_id / _signature)
    public record PaymentConfirmation(String orderId, String paymentId, String signature) {}

    public record FeeCheckRequest(Boolean found, String remark) {}

    public record UpiPayment(String utr) {}

    public record BookingRequest(LocalDateTime slot) {}

    public record UserRequest(String name, String email, String password, String role, String department,
                              String rollNumber, String course, String phone, Boolean active,
                              String resetPassword) {}

    public record ServiceRequest(String name, String description, String finalStatus, Boolean requiresVisit,
                                 Boolean active, Integer feeAmount) {}

    public record StageRequest(String name, String handlerRole, String department, Boolean optional) {}

    public record DocumentRequest(String documentName, Boolean mandatory) {}
}
