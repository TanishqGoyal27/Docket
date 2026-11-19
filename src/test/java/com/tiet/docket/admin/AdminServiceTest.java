package com.tiet.docket.admin;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.StageRepository;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional   // Every test rolls back, preserving clean state
class AdminServiceTest {

    @Autowired private AdminService adminService;
    @Autowired private WorkflowService workflowService;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private RequiredDocumentRepository requiredDocumentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User student;
    private User adminOfficer;
    private User financeOfficer;
    private User adminUser;

    @BeforeEach
    void setUp() {
        student        = createTestUser("test-admin-student@docket.com", Role.STUDENT);
        adminOfficer   = createTestUser("test-admin-officer@docket.com", Role.ADMIN_OFFICER);
        financeOfficer = createTestUser("test-admin-finance@docket.com", Role.FINANCE_OFFICER);
        adminUser      = createTestUser("test-system-admin@docket.com", Role.ADMINISTRATOR);
    }

    // ---------------------------------------------------------------
    // 1. Creating a user stores a hashed password and the right role
    // ---------------------------------------------------------------
    @Test
    void creatingUserStoresHashedPasswordAndCorrectRole() {
        User created = adminService.createUser(
                "Test Alice", "test-alice@docket.com", "plainTextPass123",
                Role.ADMIN_OFFICER, "Administration", null, null, "9998887776");

        assertNotNull(created.getId());
        assertEquals("test-alice@docket.com", created.getEmail());
        assertEquals(Role.ADMIN_OFFICER, created.getRole());
        assertEquals("Administration", created.getDepartment());
        assertTrue(created.isActive());

        // Password must be hashed with BCrypt, not stored in plain text
        assertNotEquals("plainTextPass123", created.getPassword());
        assertTrue(passwordEncoder.matches("plainTextPass123", created.getPassword()));
    }

    // ---------------------------------------------------------------
    // 2. Duplicate email is rejected
    // ---------------------------------------------------------------
    @Test
    void duplicateEmailIsRejected() {
        adminService.createUser(
                "First User", "test-unique-check@docket.com", "password123",
                Role.STUDENT, null, null, null, null);

        // Attempting to create another user with the same email
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminService.createUser(
                        "Second User", "test-unique-check@docket.com", "password123",
                        Role.PROFESSOR, null, null, null, null));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // ---------------------------------------------------------------
    // 3. Password shorter than 8 characters is rejected
    // ---------------------------------------------------------------
    @Test
    void passwordShorterThanEightCharactersIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminService.createUser(
                        "Shorty", "test-short-pwd@docket.com", "pass123",
                        Role.STUDENT, null, null, null, null));
        assertTrue(ex.getMessage().contains("at least 8 characters"));
    }

    // ---------------------------------------------------------------
    // 4. An administrator cannot deactivate themselves or change their own role
    // ---------------------------------------------------------------
    @Test
    void administratorCannotDeactivateThemselvesOrChangeOwnRole() {
        Long myId = adminUser.getId();

        // Administrator attempts to deactivate their own account
        IllegalStateException deactEx = assertThrows(IllegalStateException.class, () ->
                adminService.updateUser(myId, myId, adminUser.getName(), Role.ADMINISTRATOR,
                        adminUser.getDepartment(), adminUser.getPhone(), adminUser.getCourse(),
                        adminUser.getRollNumber(), false, null));
        assertTrue(deactEx.getMessage().contains("cannot deactivate your own"));

        // Administrator attempts to demote their own role
        IllegalStateException roleEx = assertThrows(IllegalStateException.class, () ->
                adminService.updateUser(myId, myId, adminUser.getName(), Role.STUDENT,
                        adminUser.getDepartment(), adminUser.getPhone(), adminUser.getCourse(),
                        adminUser.getRollNumber(), true, null));
        assertTrue(roleEx.getMessage().contains("cannot change your own administrator role"));
    }

    // ---------------------------------------------------------------
    // 5. Editing a user changes role and active
    // ---------------------------------------------------------------
    @Test
    void editingUserChangesRoleAndActive() {
        User target = adminService.createUser(
                "Target User", "test-target@docket.com", "password123",
                Role.STUDENT, null, null, null, null);

        // Another administrator modifies the user
        adminService.updateUser(target.getId(), adminUser.getId(), "Target Renamed",
                Role.SCHOLARSHIP_OFFICER, "Scholarships", "9911223344",
                null, null, false, "newPassword456");

        User refreshed = userRepository.findById(target.getId()).orElseThrow();
        assertEquals("Target Renamed", refreshed.getName());
        assertEquals(Role.SCHOLARSHIP_OFFICER, refreshed.getRole());
        assertEquals("Scholarships", refreshed.getDepartment());
        assertFalse(refreshed.isActive());
        assertTrue(passwordEncoder.matches("newPassword456", refreshed.getPassword()));
    }

    // ---------------------------------------------------------------
    // 6. Adding a stage appends with stepOrder = max + 1 and the engine moves an application to it
    // ---------------------------------------------------------------
    @Test
    void addingStageAppendsMaxPlusOneAndEngineAdvancesToIt() {
        com.tiet.docket.model.Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        idCard.setFeeAmount(0);   // this test is about stages, not the fee check
        List<Stage> originalStages = stageRepository.findByServiceOrderByStepOrderAsc(idCard);
        int maxOrder = originalStages.get(originalStages.size() - 1).getStepOrder();

        // 1. Append a new stage
        Stage appended = adminService.addStage(idCard.getId(), "Card Dispatch", Role.ADMIN_OFFICER, null);
        assertEquals(maxOrder + 1, appended.getStepOrder());
        assertEquals(Role.ADMIN_OFFICER, appended.getHandlerRole());

        // 2. Submit an ID Card application
        Application app = new Application();
        app.setStudent(student);
        app.setService(idCard);
        app = workflowService.submit(app);

        // 3. Approve the stage that used to be the last one.
        // Since a new stage now exists, it should move there instead of finishing!
        app = workflowService.approve(app.getId(), adminOfficer);
        assertEquals(ApplicationStatus.UNDER_REVIEW, app.getStatus());
        assertNotNull(app.getCurrentStage());
        assertEquals(appended.getId(), app.getCurrentStage().getId());
        assertEquals(maxOrder + 1, app.getCurrentStage().getStepOrder());

        // 4. Approve the new stage -> now it finishes as APPROVED
        app = workflowService.approve(app.getId(), adminOfficer);
        assertEquals(ApplicationStatus.APPROVED, app.getStatus());
        assertNull(app.getCurrentStage());
    }

    // ---------------------------------------------------------------
    // 7. Editing a stage changes its handlerRole
    // ---------------------------------------------------------------
    @Test
    void editingStageChangesHandlerRole() {
        com.tiet.docket.model.Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        Stage stage1 = stageRepository.findByServiceOrderByStepOrderAsc(idCard).get(0);
        int originalOrder = stage1.getStepOrder();

        adminService.updateStage(stage1.getId(), "Updated Document Review", Role.HOD, "Computer Science");

        Stage refreshed = stageRepository.findById(stage1.getId()).orElseThrow();
        assertEquals("Updated Document Review", refreshed.getName());
        assertEquals(Role.HOD, refreshed.getHandlerRole());
        assertEquals("Computer Science", refreshed.getDepartment());
        assertEquals(originalOrder, refreshed.getStepOrder()); // stepOrder must remain unchanged
    }

    // ---------------------------------------------------------------
    // 8. Adding a required document works and a duplicate name is rejected
    // ---------------------------------------------------------------
    @Test
    void addingRequiredDocumentWorksAndDuplicateIsRejected() {
        com.tiet.docket.model.Service idCard = serviceRepository.findByName("ID Card").orElseThrow();

        RequiredDocument doc = adminService.addRequiredDocument(idCard.getId(), "Blood Group Certificate", true);
        assertNotNull(doc.getId());
        assertEquals("Blood Group Certificate", doc.getDocumentName());
        assertTrue(doc.isMandatory());

        // Duplicate name for the same service is rejected
        IllegalArgumentException dupEx = assertThrows(IllegalArgumentException.class, () ->
                adminService.addRequiredDocument(idCard.getId(), "Blood Group Certificate", false));
        assertTrue(dupEx.getMessage().contains("already exists"));

        // Toggle mandatory flag
        adminService.toggleRequiredDocumentMandatory(doc.getId());
        RequiredDocument toggled = requiredDocumentRepository.findById(doc.getId()).orElseThrow();
        assertFalse(toggled.isMandatory());
    }

    // ---------------------------------------------------------------
    // 9. Toggling a service inactive is saved
    // ---------------------------------------------------------------
    @Test
    void togglingServiceInactiveIsSaved() {
        com.tiet.docket.model.Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        assertTrue(idCard.isActive());

        // Deactivate service
        adminService.updateService(idCard.getId(), "Temporarily suspended", false);
        com.tiet.docket.model.Service inactive = serviceRepository.findById(idCard.getId()).orElseThrow();
        assertFalse(inactive.isActive());
        assertEquals("Temporarily suspended", inactive.getDescription());

        // Reactivate service
        adminService.updateService(idCard.getId(), "Apply for student ID card", true);
        com.tiet.docket.model.Service active = serviceRepository.findById(idCard.getId()).orElseThrow();
        assertTrue(active.isActive());
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------
    private User createTestUser(String email, Role role) {
        User user = new User();
        user.setName(email);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }
}
