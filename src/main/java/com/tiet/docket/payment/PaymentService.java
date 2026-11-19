package com.tiet.docket.payment;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.FeePayment;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.FeePaymentRepository;
import com.tiet.docket.workflow.NotificationService;
import com.tiet.docket.workflow.SupervisorApprovalService;
import com.tiet.docket.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Application fee: the student pays after filling the form, the payment is
 * checked against the gateway signature, a receipt is generated and the
 * application is submitted. The Finance Officer then cross-checks every paid
 * fee in parallel with the Administrative Officer's document check.
 */
@Service
public class PaymentService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final PaymentGateway gateway;
    private final FeePaymentRepository feePaymentRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final DecisionHistoryRepository historyRepository;
    private final WorkflowService workflowService;
    private final NotificationService notificationService;
    private final SupervisorApprovalService supervisorApprovalService;
    private final String upiId;
    private final String upiName;

    public PaymentService(PaymentGateway gateway, FeePaymentRepository feePaymentRepository,
                          ApplicationRepository applicationRepository,
                          ApplicationDocumentRepository documentRepository,
                          DecisionHistoryRepository historyRepository,
                          WorkflowService workflowService, NotificationService notificationService,
                          SupervisorApprovalService supervisorApprovalService,
                          @Value("${docket.payment.upi-id:}") String upiId,
                          @Value("${docket.payment.upi-name:TIET Fees}") String upiName) {
        this.gateway = gateway;
        this.feePaymentRepository = feePaymentRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
        this.supervisorApprovalService = supervisorApprovalService;
        this.upiId = upiId.isBlank() ? DEMO_UPI_ID : upiId.trim();
        this.upiName = upiName;
    }

    /** What the browser needs to open the checkout. */
    public record CheckoutOrder(String gateway, String keyId, String orderId, long amountPaise, String currency,
                                String referenceNumber, String serviceName, String name, String email,
                                String phone, String upiId, String upiName, boolean upiDemo) {}

    // Placeholder that UPI apps reject, so a test scan can never send money to a real person
    static final String DEMO_UPI_ID = "docket-demo@examplebank";

    public PaymentGateway gateway() {
        return gateway;
    }

    @Transactional
    public CheckoutOrder startPayment(Long applicationId, User student) {
        Application app = ownApplication(applicationId, student);
        if (app.getStatus() != ApplicationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("This application does not need a payment");
        }
        int fee = app.getService().getFeeAmount();
        if (fee <= 0) {
            throw new IllegalStateException("This service has no fee");
        }
        FeePayment payment = feePaymentRepository.findByApplication(app).orElseGet(FeePayment::new);
        if (payment.getStatus() == FeePayment.Status.PAID) {
            throw new IllegalStateException("The fee for this application is already paid");
        }
        // Every attempt gets a new order; an abandoned checkout simply leaves the old one unused
        payment.setApplication(app);
        payment.setAmount(fee);
        payment.setGateway(gateway.name());
        payment.setOrderId(gateway.createOrder(fee * 100L, app.getReferenceNumber()));
        feePaymentRepository.save(payment);

        User s = app.getStudent();
        return new CheckoutOrder(gateway.name(), gateway.publicKey(), payment.getOrderId(), fee * 100L, "INR",
                app.getReferenceNumber(), app.getService().getName(), s.getName(), s.getEmail(), s.getPhone(),
                upiId, upiName, DEMO_UPI_ID.equals(upiId));
    }

    /** Sandbox only: acts as the gateway's checkout completing a payment. */
    @Transactional(readOnly = true)
    public SandboxGateway.SimulatedPayment sandboxPay(String orderId, User student) {
        if (!(gateway instanceof SandboxGateway sandbox)) {
            throw new IllegalStateException("The sandbox gateway is not active");
        }
        FeePayment payment = feePaymentRepository.findByOrderId(orderId)
                .filter(p -> p.getApplication().getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return sandbox.pay(payment.getOrderId());
    }

    /** Called with the gateway's response; submits the application once the payment is genuine. */
    @Transactional
    public Application completePayment(Long applicationId, User student, String orderId, String paymentId,
                                       String signature) {
        Application app = ownApplication(applicationId, student);
        FeePayment payment = feePaymentRepository.findByApplication(app)
                .orElseThrow(() -> new IllegalStateException("Start the payment first"));
        if (payment.getStatus() == FeePayment.Status.PAID) {
            throw new IllegalStateException("The fee for this application is already paid");
        }
        if (!payment.getOrderId().equals(orderId) || !gateway.verifySignature(orderId, paymentId, signature)) {
            throw new IllegalArgumentException("The payment could not be verified. No application was submitted.");
        }

        payment.setPaymentId(paymentId);
        return finishPayment(app, student, payment);
    }

    /**
     * UPI: the student paid the institute's UPI ID from their own app (QR or UPI ID) and enters the
     * 12-digit UTR it shows. Docket cannot see the bank, so the Finance Officer confirms the UTR
     * against the bank statement before the final approval.
     */
    @Transactional
    public Application completeUpiPayment(Long applicationId, User student, String utr) {
        Application app = ownApplication(applicationId, student);
        if (app.getStatus() != ApplicationStatus.PAYMENT_PENDING || app.getService().getFeeAmount() <= 0) {
            throw new IllegalStateException("This application does not need a payment");
        }
        String ref = utr == null ? "" : utr.trim();
        if (!ref.matches("\\d{12}")) {
            throw new IllegalArgumentException("Enter the 12-digit UTR / UPI reference number shown in your UPI app");
        }
        if (feePaymentRepository.existsByPaymentId(ref)) {
            throw new IllegalArgumentException("This UTR has already been used for another payment");
        }
        FeePayment payment = feePaymentRepository.findByApplication(app).orElseGet(FeePayment::new);
        if (payment.getStatus() == FeePayment.Status.PAID) {
            throw new IllegalStateException("The fee for this application is already paid");
        }
        payment.setApplication(app);
        payment.setAmount(app.getService().getFeeAmount());
        payment.setGateway("UPI");
        payment.setOrderId(null);
        payment.setPaymentId(ref);
        return finishPayment(app, student, payment);
    }

    // Records the payment, attaches the receipt and submits the application
    private Application finishPayment(Application app, User student, FeePayment payment) {
        payment.setStatus(FeePayment.Status.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setVerification(FeePayment.Verification.PENDING);
        feePaymentRepository.save(payment);
        payment.setReceiptNumber(String.format("RCPT-%d-%04d", LocalDate.now().getYear(), payment.getId()));
        feePaymentRepository.save(payment);

        attachReceipt(app, payment);
        Application submitted = app.getService().isRequiresSupervisorApproval()
                ? supervisorApprovalService.start(app)
                : workflowService.submit(app);

        notificationService.notifyUser(student, submitted, "Payment of Rs. " + payment.getAmount()
                + " received for " + submitted.getReferenceNumber() + ". Receipt " + payment.getReceiptNumber() + ".");
        notificationService.notifyRole(Role.FINANCE_OFFICER, null, submitted, "Fee payment "
                + payment.getPaymentId() + " for " + submitted.getReferenceNumber() + " is waiting for verification.");
        return submitted;
    }

    // ---------------------------------------------------------------
    // Finance Officer cross-check (runs in parallel with document review)
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FeePayment> paidFees() {
        return feePaymentRepository.findByStatusOrderByPaidAtDesc(FeePayment.Status.PAID);
    }

    @Transactional
    public FeePayment verifyFee(Long applicationId, User officer, boolean found, String remark) {
        if (officer.getRole() != Role.FINANCE_OFFICER) {
            throw new IllegalStateException("Only the Finance Officer can verify fee payments");
        }
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        FeePayment payment = feePaymentRepository.findByApplication(app)
                .filter(p -> p.getStatus() == FeePayment.Status.PAID)
                .orElseThrow(() -> new IllegalStateException("No fee payment was made for this application"));
        if (payment.getVerification() != FeePayment.Verification.PENDING) {
            throw new IllegalStateException("This fee payment has already been checked");
        }
        if (!found && (remark == null || remark.isBlank())) {
            throw new IllegalArgumentException("Say why the payment could not be found");
        }

        payment.setVerification(found ? FeePayment.Verification.CONFIRMED : FeePayment.Verification.NOT_FOUND);
        payment.setVerifiedBy(officer);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        feePaymentRepository.save(payment);

        if (found) {
            record(app, officer, DecisionType.VERIFIED, "Fee payment " + payment.getPaymentId() + " confirmed");
            notificationService.notifyUser(app.getStudent(), app,
                    "Finance confirmed your fee payment for " + app.getReferenceNumber() + ".");
        } else {
            record(app, officer, DecisionType.REJECTED, "Fee payment not found: " + remark.trim());
            if (app.getCurrentStage() != null) {
                app.setCurrentStage(null);
                app.setStatus(ApplicationStatus.REJECTED);
                applicationRepository.save(app);
            }
            notificationService.notifyUser(app.getStudent(), app, "Application " + app.getReferenceNumber()
                    + " was rejected: the fee payment could not be found. Reason: " + remark.trim());
        }
        return payment;
    }

    // ---------------------------------------------------------------

    private Application ownApplication(Long id, User student) {
        return applicationRepository.findById(id)
                .filter(a -> a.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
    }

    private void record(Application app, User officer, DecisionType decision, String reason) {
        DecisionHistory h = new DecisionHistory();
        h.setApplication(app);
        h.setStage(app.getCurrentStage());
        h.setOfficer(officer);
        h.setDecision(decision);
        h.setReason(reason);
        historyRepository.save(h);
    }

    // The system-generated receipt is stored in the database like any other document
    private void attachReceipt(Application app, FeePayment p) {
        User s = app.getStudent();
        byte[] pdf = ReceiptPdf.build("FEE RECEIPT - Docket, TIET Patiala", List.of(
                "Digital Campus Administration Portal",
                "",
                "Receipt number:    " + p.getReceiptNumber(),
                "Date:              " + p.getPaidAt().format(WHEN),
                "Application:       " + app.getReferenceNumber() + " (" + app.getService().getName() + ")",
                "Student:           " + s.getName() + (s.getRollNumber() == null ? "" : " (Roll " + s.getRollNumber() + ")"),
                "Email:             " + s.getEmail(),
                "",
                "Amount paid:       Rs. " + p.getAmount() + ".00",
                "Payment ID:        " + p.getPaymentId(),
                "Order ID:          " + p.getOrderId(),
                "Paid through:      " + switch (p.getGateway()) {
                    case "SANDBOX" -> "Docket Sandbox (test mode, no real money)";
                    case "UPI" -> "UPI to " + upiId + " (UTR entered by the student)";
                    default -> "Razorpay";
                },
                "Status:            PAID - subject to verification by the Finance Officer",
                "",
                "This is a computer-generated receipt and needs no signature."));

        ApplicationDocument doc = new ApplicationDocument();
        doc.setApplication(app);
        doc.setFeeReceipt(true);
        doc.setFileName(p.getReceiptNumber() + ".pdf");
        doc.setFileType("application/pdf");
        doc.setFileSize(pdf.length);
        doc.setData(pdf);
        documentRepository.save(doc);
    }
}
