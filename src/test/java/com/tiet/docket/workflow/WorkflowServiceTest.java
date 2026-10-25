package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.PaymentRecord;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.PaymentRecordRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional   // every test is rolled back, so tests never affect each other
class WorkflowServiceTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private DecisionHistoryRepository decisionHistoryRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;

    private User student;
    private User otherStudent;
    private User adminOfficer;
    private User financeOfficer;
    private User professor;
    private User hod;
    private User accountsOfficer;
    private User administrator;

    @BeforeEach
    void setUp() {
        // Unique "test-" emails so they never clash with the seeded test users
        student         = createUser("test-student@docket.com", Role.STUDENT);
        otherStudent    = createUser("test-student2@docket.com", Role.STUDENT);
        adminOfficer    = createUser("test-admin@docket.com", Role.ADMIN_OFFICER);
        financeOfficer  = createUser("test-finance@docket.com", Role.FINANCE_OFFICER);
        professor       = createUser("test-prof@docket.com", Role.PROFESSOR);
        hod             = createUser("test-hod@docket.com", Role.HOD);
        accountsOfficer = createUser("test-accounts@docket.com", Role.ACCOUNTS_OFFICER);
        administrator   = createUser("test-admin-role@docket.com", Role.ADMINISTRATOR);
    }

    // ----------------------------------------------------------------
    // 1. Submit
    // ----------------------------------------------------------------
    @Test
    void submitPlacesApplicationAtStepOneWithReferenceNumber() {
        Application app = submitApplication("ID Card");

        assertEquals(ApplicationStatus.SUBMITTED, app.getStatus());
        assertEquals(1, app.getCurrentStage().getStepOrder());
        assertEquals(Role.ADMIN_OFFICER, app.getCurrentStage().getHandlerRole());
        assertNotNull(app.getReferenceNumber());
        assertTrue(app.getReferenceNumber().startsWith("DKT-"));
    }

    // ----------------------------------------------------------------
    // 2. ID Card: the full happy path through all 3 stages
    // ----------------------------------------------------------------
    @Test
    void idCardMovesThroughAllStagesAndEndsApproved() {
        Application app = submitApplication("ID Card");

        app = workflowService.approve(app.getId(), adminOfficer);     // stage 1 -> 2
        assertEquals(2, app.getCurrentStage().getStepOrder());
        assertEquals(ApplicationStatus.UNDER_REVIEW, app.getStatus());

        app = workflowService.approve(app.getId(), financeOfficer);   // stage 2 -> 3
        assertEquals(3, app.getCurrentStage().getStepOrder());

        app = workflowService.approve(app.getId(), adminOfficer);     // stage 3 -> done
        assertEquals(ApplicationStatus.APPROVED, app.getStatus());
        assertNull(app.getCurrentStage());

        // One history row per decision
        assertEquals(3, decisionHistoryRepository
                .findByApplicationOrderByDecidedAtAsc(app).size());
    }

    // ----------------------------------------------------------------
    // 3. Reimbursement: 4 stages, ends PROCESSED (not APPROVED)
    // ----------------------------------------------------------------
    @Test
    void reimbursementEndsProcessedAfterAccountsOfficer() {
        Application app = submitApplication("Reimbursement");

        app = workflowService.approve(app.getId(), professor);
        app = workflowService.approve(app.getId(), hod);
        app = workflowService.approve(app.getId(), financeOfficer);
        app = workflowService.recordPaymentAndProcess(
                app.getId(), accountsOfficer, "UTR-123456", LocalDate.now());

        assertEquals(ApplicationStatus.PROCESSED, app.getStatus());
        assertNull(app.getCurrentStage());
    }

    @Test
    void accountsOfficerCannotApproveWithoutPaymentRecord() {
        Application app = submitApplication("Reimbursement");

        app = workflowService.approve(app.getId(), professor);
        app = workflowService.approve(app.getId(), hod);
        app = workflowService.approve(app.getId(), financeOfficer);

        Long appId = app.getId();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appId, accountsOfficer));
        assertEquals("Record the payment reference and date before processing", ex.getMessage());
    }

    // ----------------------------------------------------------------
    // 4. Role checks
    // ----------------------------------------------------------------
    @Test
    void wrongRoleCannotApprove() {
        Application app = submitApplication("ID Card");   // waiting for ADMIN_OFFICER

        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(app.getId(), financeOfficer));
    }

    @Test
    void studentCannotApprove() {
        Application app = submitApplication("ID Card");

        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(app.getId(), student));
    }

    // ----------------------------------------------------------------
    // 5. Rejection needs a reason, and ends the application
    // ----------------------------------------------------------------
    @Test
    void rejectRequiresReason() {
        Application app = submitApplication("ID Card");

        assertThrows(IllegalArgumentException.class,
                () -> workflowService.reject(app.getId(), adminOfficer, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.reject(app.getId(), adminOfficer, null));
    }

    @Test
    void rejectStoresReasonAndEndsApplication() {
        Application app = submitApplication("ID Card");

        app = workflowService.reject(app.getId(), adminOfficer, "Photo is not clear");

        assertEquals(ApplicationStatus.REJECTED, app.getStatus());
        assertNull(app.getCurrentStage());

        var history = decisionHistoryRepository.findByApplicationOrderByDecidedAtAsc(app);
        assertEquals(1, history.size());
        assertEquals(DecisionType.REJECTED, history.get(0).getDecision());
        assertEquals("Photo is not clear", history.get(0).getReason());
    }

    // ----------------------------------------------------------------
    // 6. Correction and resubmit
    // ----------------------------------------------------------------
    @Test
    void correctionSendsBackToStudentAndKeepsSameStage() {
        Application app = submitApplication("ID Card");
        app = workflowService.approve(app.getId(), adminOfficer);   // now at stage 2 (finance)
        Long stageBefore = app.getCurrentStage().getId();

        app = workflowService.requestCorrection(app.getId(), financeOfficer, "Fee receipt missing");
        assertEquals(ApplicationStatus.AWAITING_CORRECTION, app.getStatus());
        assertEquals(stageBefore, app.getCurrentStage().getId());

        // Staff cannot act while the application waits for the student
        Long id = app.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(id, financeOfficer));

        // Another student cannot resubmit it
        assertThrows(IllegalStateException.class,
                () -> workflowService.resubmit(id, otherStudent));

        // The owner can; review resumes at the SAME stage
        app = workflowService.resubmit(id, student);
        assertEquals(ApplicationStatus.UNDER_REVIEW, app.getStatus());
        assertEquals(stageBefore, app.getCurrentStage().getId());
    }

    @Test
    void requestCorrectionRequiresMessage() {
        Application app = submitApplication("ID Card");

        assertThrows(IllegalArgumentException.class,
                () -> workflowService.requestCorrection(app.getId(), adminOfficer, ""));
    }

    @Test
    void cannotResubmitAnApplicationThatIsNotAwaitingCorrection() {
        Application app = submitApplication("ID Card");

        assertThrows(IllegalStateException.class,
                () -> workflowService.resubmit(app.getId(), student));
    }

    // ----------------------------------------------------------------
    // 7. Staff queue only shows applications for the officer's role
    // ----------------------------------------------------------------
    @Test
    void pendingQueueIsFilteredByRole() {
        Application app = submitApplication("ID Card");   // at stage 1, ADMIN_OFFICER

        List<Application> adminQueue = workflowService.getPendingFor(adminOfficer);
        List<Application> financeQueue = workflowService.getPendingFor(financeOfficer);

        assertTrue(adminQueue.stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertFalse(financeQueue.stream().anyMatch(a -> a.getId().equals(app.getId())));
    }

    // ----------------------------------------------------------------
    // 8. PaymentRecord and Accounts Officer (Task 1)
    // ----------------------------------------------------------------
    @Test
    void recordPaymentAndProcessSavesRecordAndEndsProcessed() {
        Application app = submitApplication("Reimbursement");

        app = workflowService.approve(app.getId(), professor);
        app = workflowService.approve(app.getId(), hod);
        app = workflowService.approve(app.getId(), financeOfficer);

        LocalDate paymentDate = LocalDate.now();
        app = workflowService.recordPaymentAndProcess(
                app.getId(), accountsOfficer, "UTR-998877", paymentDate);

        assertEquals(ApplicationStatus.PROCESSED, app.getStatus());
        assertNull(app.getCurrentStage());

        PaymentRecord record = paymentRecordRepository.findByApplication(app).orElseThrow();
        assertEquals("UTR-998877", record.getPaymentReference());
        assertEquals(paymentDate, record.getPaymentDate());
        assertEquals(accountsOfficer.getId(), record.getRecordedBy().getId());
        assertNotNull(record.getRecordedAt());

        var history = decisionHistoryRepository.findByApplicationOrderByDecidedAtAsc(app);
        assertEquals(4, history.size());
        assertEquals(DecisionType.PROCESSED, history.get(3).getDecision());
    }

    @Test
    void recordPaymentAndProcessRejectsBlankReferenceOrNullDate() {
        Application app = submitApplication("Reimbursement");

        app = workflowService.approve(app.getId(), professor);
        app = workflowService.approve(app.getId(), hod);
        app = workflowService.approve(app.getId(), financeOfficer);

        Long appId = app.getId();
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.recordPaymentAndProcess(appId, accountsOfficer, "", LocalDate.now()));
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.recordPaymentAndProcess(appId, accountsOfficer, "   ", LocalDate.now()));
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.recordPaymentAndProcess(appId, accountsOfficer, null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.recordPaymentAndProcess(appId, accountsOfficer, "UTR-123", null));
    }

    @Test
    void nonAccountsOfficerCannotRecordPayment() {
        Application app = submitApplication("Reimbursement");

        app = workflowService.approve(app.getId(), professor);
        app = workflowService.approve(app.getId(), hod);
        app = workflowService.approve(app.getId(), financeOfficer);

        // At stage 4 (waiting for ACCOUNTS_OFFICER), finance officer tries to record payment
        Long appId = app.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.recordPaymentAndProcess(appId, financeOfficer, "UTR-123", LocalDate.now()));
    }

    @Test
    void recordPaymentAndProcessFailsWhenNotAtPaymentStage() {
        // Reimbursement is at stage 1 (Professor verification)
        Application app = submitApplication("Reimbursement");
        Long appId = app.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.recordPaymentAndProcess(appId, accountsOfficer, "UTR-123", LocalDate.now()));

        // ID Card stage 1 is handled by ADMIN_OFFICER (not ACCOUNTS_OFFICER)
        Application idCardApp = submitApplication("ID Card");
        Long idCardId = idCardApp.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.recordPaymentAndProcess(idCardId, adminOfficer, "UTR-123", LocalDate.now()));
    }

    // ----------------------------------------------------------------
    // 9. Details Correction and Student Record Update (Task 2)
    // ----------------------------------------------------------------
    @Test
    void detailsCorrectionApprovalUpdatesStudentPhoneAndClosesApplication() {
        Application app = submitApplication("Details Correction",
                "{\"field\":\"phone\",\"newValue\":\"9999999999\"}");

        // Stage 1: Initial review by ADMIN_OFFICER
        app = workflowService.approve(app.getId(), adminOfficer);
        assertEquals(ApplicationStatus.UNDER_REVIEW, app.getStatus());
        assertEquals(2, app.getCurrentStage().getStepOrder());

        // Stage 2: Correction approval by ADMINISTRATOR (last stage)
        app = workflowService.approve(app.getId(), administrator);
        assertEquals(ApplicationStatus.CLOSED, app.getStatus());
        assertNull(app.getCurrentStage());

        User updatedStudent = userRepository.findById(student.getId()).orElseThrow();
        assertEquals("9999999999", updatedStudent.getPhone());

        var history = decisionHistoryRepository.findByApplicationOrderByDecidedAtAsc(app);
        assertEquals(2, history.size());
        assertEquals(DecisionType.APPROVED, history.get(1).getDecision());
    }

    @Test
    void detailsCorrectionApprovalUpdatesOtherWhitelistFields() {
        // Test "name"
        Application app1 = submitApplication("Details Correction",
                "{\"field\":\"name\",\"newValue\":\"New Student Name\"}");
        app1 = workflowService.approve(app1.getId(), adminOfficer);
        app1 = workflowService.approve(app1.getId(), administrator);
        assertEquals(ApplicationStatus.CLOSED, app1.getStatus());
        assertEquals("New Student Name", userRepository.findById(student.getId()).orElseThrow().getName());

        // Test "course"
        Application app2 = submitApplication("Details Correction",
                "{\"field\":\"course\",\"newValue\":\"B.Tech CSE\"}");
        app2 = workflowService.approve(app2.getId(), adminOfficer);
        app2 = workflowService.approve(app2.getId(), administrator);
        assertEquals(ApplicationStatus.CLOSED, app2.getStatus());
        assertEquals("B.Tech CSE", userRepository.findById(student.getId()).orElseThrow().getCourse());

        // Test "rollNumber"
        Application app3 = submitApplication("Details Correction",
                "{\"field\":\"rollNumber\",\"newValue\":\"10203040\"}");
        app3 = workflowService.approve(app3.getId(), adminOfficer);
        app3 = workflowService.approve(app3.getId(), administrator);
        assertEquals(ApplicationStatus.CLOSED, app3.getStatus());
        assertEquals("10203040", userRepository.findById(student.getId()).orElseThrow().getRollNumber());
    }

    @Test
    void detailsCorrectionRejectsNonWhitelistFieldAndLeavesUserUnchanged() {
        String originalEmail = student.getEmail();
        Application app = submitApplication("Details Correction",
                "{\"field\":\"email\",\"newValue\":\"hacked@docket.com\"}");

        app = workflowService.approve(app.getId(), adminOfficer);

        Long appId = app.getId();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appId, administrator));
        assertTrue(ex.getMessage().contains("not allowed"));

        // Student's email remains unchanged
        User refreshed = userRepository.findById(student.getId()).orElseThrow();
        assertEquals(originalEmail, refreshed.getEmail());
    }

    @Test
    void detailsCorrectionRejectsBlankOrMissingFormData() {
        // Missing formData (null)
        Application appNull = submitApplication("Details Correction", null);
        appNull = workflowService.approve(appNull.getId(), adminOfficer);
        Long appNullId = appNull.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appNullId, administrator));

        // Blank formData
        Application appBlank = submitApplication("Details Correction", "   ");
        appBlank = workflowService.approve(appBlank.getId(), adminOfficer);
        Long appBlankId = appBlank.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appBlankId, administrator));

        // Blank newValue
        Application appBlankVal = submitApplication("Details Correction",
                "{\"field\":\"phone\",\"newValue\":\"   \"}");
        appBlankVal = workflowService.approve(appBlankVal.getId(), adminOfficer);
        Long appBlankValId = appBlankVal.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appBlankValId, administrator));

        // Missing field key
        Application appNoField = submitApplication("Details Correction",
                "{\"newValue\":\"12345\"}");
        appNoField = workflowService.approve(appNoField.getId(), adminOfficer);
        Long appNoFieldId = appNoField.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appNoFieldId, administrator));

        // Missing newValue key
        Application appNoVal = submitApplication("Details Correction",
                "{\"field\":\"phone\"}");
        appNoVal = workflowService.approve(appNoVal.getId(), adminOfficer);
        Long appNoValId = appNoVal.getId();
        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(appNoValId, administrator));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------
    private User createUser(String email, Role role) {
        User user = new User();
        user.setName(email);
        user.setEmail(email);
        user.setPassword("not-a-real-password");   // login is not tested here
        user.setRole(role);
        return userRepository.save(user);
    }

    private Application submitApplication(String serviceName) {
        return submitApplication(serviceName, null);
    }

    private Application submitApplication(String serviceName, String formData) {
        Service service = serviceRepository.findByName(serviceName).orElseThrow();
        Application app = new Application();
        app.setStudent(student);
        app.setService(service);
        app.setFormData(formData);
        return workflowService.submit(app);
    }
}