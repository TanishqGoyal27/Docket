package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.Notification;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.NotificationRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NotificationService.
 *
 * @SpringBootTest loads the full application context (including the H2 in-memory DB).
 * @Transactional means every test rolls back after it finishes, so tests never
 * interfere with each other.
 */
@SpringBootTest
@Transactional   // roll back after every test
class NotificationServiceTest {

    @Autowired private NotificationService notificationService;
    @Autowired private WorkflowService workflowService;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private NotificationRepository notificationRepository;

    // Users created fresh before each test (unique "test-" emails)
    private User student;
    private User adminOfficer;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        student      = createUser("test-notif-student@docket.com",  Role.STUDENT);
        adminOfficer = createUser("test-notif-admin@docket.com",    Role.ADMIN_OFFICER);
        anotherUser  = createUser("test-notif-other@docket.com",    Role.STUDENT);
    }

    // ----------------------------------------------------------------
    // 1. submit() creates a notification for the student AND for ADMIN_OFFICER
    // ----------------------------------------------------------------
    @Test
    void submitCreatesNotificationsForStudentAndAdminOfficer() {
        Application app = submitApplication("ID Card");

        // The student must have received at least one notification.
        List<Notification> studentNotes = notificationService.getForUser(student);
        assertFalse(studentNotes.isEmpty(), "Student should receive a notification on submit");
        assertTrue(studentNotes.get(0).getMessage().contains(app.getReferenceNumber()),
                "Student notification should contain the reference number");

        // The admin officer must have received at least one notification.
        List<Notification> officerNotes = notificationService.getForUser(adminOfficer);
        assertFalse(officerNotes.isEmpty(),
                "Admin officer should receive a notification on submit");
        assertTrue(officerNotes.get(0).getMessage().contains(app.getReferenceNumber()),
                "Officer notification should contain the reference number");
    }

    // ----------------------------------------------------------------
    // 2. approve() to the next stage notifies the role of the next stage
    // ----------------------------------------------------------------
    @Test
    void approveToNextStageNotifiesNextRole() {
        // Create a FINANCE_OFFICER so they can receive the notification
        // when the ID Card application moves to "Fee verification" (stage 2).
        User financeOfficer = createUser("test-notif-finance@docket.com", Role.FINANCE_OFFICER);

        Application app = submitApplication("ID Card");

        // Admin approves stage 1 -> application moves to stage 2 (Finance Officer)
        workflowService.approve(app.getId(), adminOfficer);

        // Finance officer should now have a notification.
        List<Notification> financeNotes = notificationService.getForUser(financeOfficer);
        assertFalse(financeNotes.isEmpty(),
                "Finance officer should receive a notification after stage 1 approval");
        assertTrue(financeNotes.get(0).getMessage().contains(app.getReferenceNumber()),
                "Finance notification should contain the reference number");
    }

    // ----------------------------------------------------------------
    // 3. reject() notification contains the reason
    // ----------------------------------------------------------------
    @Test
    void rejectNotificationContainsReason() {
        Application app = submitApplication("ID Card");
        String reason = "Documents are incomplete";

        workflowService.reject(app.getId(), adminOfficer, reason);

        List<Notification> studentNotes = notificationService.getForUser(student);
        // Filter to find the rejection notification (student may have multiple)
        boolean found = studentNotes.stream()
                .anyMatch(n -> n.getMessage().contains(reason));
        assertTrue(found, "Rejection notification should contain the rejection reason");
    }

    // ----------------------------------------------------------------
    // 4. markRead() throws if another user tries to mark your notification
    // ----------------------------------------------------------------
    @Test
    void markReadRejectsAnotherUsersNotification() {
        // Create a notification for 'student'
        Notification n = notificationService.notifyUser(student, null, "Hello student");

        // 'anotherUser' must NOT be able to mark it as read
        assertThrows(IllegalStateException.class,
                () -> notificationService.markRead(n.getId(), anotherUser),
                "Marking another user's notification should throw IllegalStateException");
    }

    // ----------------------------------------------------------------
    // 5. countUnread drops to zero after markAllRead
    // ----------------------------------------------------------------
    @Test
    void countUnreadDropsAfterMarkAllRead() {
        // Send a couple of notifications to the student
        notificationService.notifyUser(student, null, "First message");
        notificationService.notifyUser(student, null, "Second message");

        long unreadBefore = notificationService.countUnread(student);
        assertTrue(unreadBefore >= 2, "Student should have at least 2 unread notifications");

        notificationService.markAllRead(student);

        long unreadAfter = notificationService.countUnread(student);
        assertEquals(0, unreadAfter, "After markAllRead, unread count should be 0");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------
    private User createUser(String email, Role role) {
        User user = new User();
        user.setName(email);
        user.setEmail(email);
        user.setPassword("not-a-real-password");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Application submitApplication(String serviceName) {
        com.tiet.docket.model.Service service =
                serviceRepository.findByName(serviceName).orElseThrow();
        Application app = new Application();
        app.setStudent(student);
        app.setService(service);
        return workflowService.submit(app);
    }
}
