package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

    private User student;
    private User otherStudent;
    private User adminOfficer;
    private User financeOfficer;
    private User professor;
    private User hod;
    private User accountsOfficer;

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
        app = workflowService.approve(app.getId(), accountsOfficer);

        assertEquals(ApplicationStatus.PROCESSED, app.getStatus());
        assertNull(app.getCurrentStage());
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
        Service service = serviceRepository.findByName(serviceName).orElseThrow();
        Application app = new Application();
        app.setStudent(student);
        app.setService(service);
        return workflowService.submit(app);
    }
}