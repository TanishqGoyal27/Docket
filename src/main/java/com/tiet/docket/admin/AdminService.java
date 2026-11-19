package com.tiet.docket.admin;

import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.StageRepository;
import com.tiet.docket.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;   // Spring's @Service, not model.Service
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the Administrator Console.
 * Manages users, workflows (services & stages), and required documents.
 *
 * NOTE: Shares the simple name "Service" with com.tiet.docket.model.Service,
 * so the entity is always referenced by its fully qualified name.
 */
@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final StageRepository stageRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        ServiceRepository serviceRepository,
                        StageRepository stageRepository,
                        RequiredDocumentRepository requiredDocumentRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.stageRepository = stageRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------------
    // 1. Dashboard statistics
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public long getUserCount() {
        return userRepository.count();
    }

    @Transactional(readOnly = true)
    public long getServiceCount() {
        return serviceRepository.count();
    }

    @Transactional(readOnly = true)
    public long getStageCount() {
        return stageRepository.count();
    }

    // ---------------------------------------------------------------
    // 2. User management
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    /**
     * Creates a new user with hashed password.
     * Name, email, password, and role are required.
     * Password must be >= 8 characters. Email must be valid and unique.
     */
    public User createUser(String name, String email, String password, Role role,
                           String department, String rollNumber, String course, String phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String trimmedEmail = email.trim().toLowerCase();
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")
                || trimmedEmail.indexOf('@') >= trimmedEmail.lastIndexOf('.')) {
            throw new IllegalArgumentException("Email must be a valid email address");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new IllegalArgumentException("A user with email " + trimmedEmail + " already exists");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(trimmedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setDepartment(department != null && !department.isBlank() ? department.trim() : null);
        user.setRollNumber(rollNumber != null && !rollNumber.isBlank() ? rollNumber.trim() : null);
        user.setCourse(course != null && !course.isBlank() ? course.trim() : null);
        user.setPhone(phone != null && !phone.isBlank() ? phone.trim() : null);
        user.setActive(true);

        return userRepository.save(user);
    }

    /**
     * Updates an existing user.
     * Safety rules: an admin cannot deactivate their own account or change their own role.
     * Email cannot be modified. If resetPassword is provided, it is validated and hashed.
     */
    public User updateUser(Long userId, Long currentAdminId, String name, Role role,
                           String department, String phone, String course, String rollNumber,
                           boolean active, String resetPassword) {
        User user = getUserById(userId);

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }

        // Safety rules to prevent administrators from locking themselves out
        if (userId.equals(currentAdminId)) {
            if (!active) {
                throw new IllegalStateException("You cannot deactivate your own administrator account");
            }
            if (role != Role.ADMINISTRATOR) {
                throw new IllegalStateException("You cannot change your own administrator role");
            }
        }

        user.setName(name.trim());
        user.setRole(role);
        user.setDepartment(department != null && !department.isBlank() ? department.trim() : null);
        user.setPhone(phone != null && !phone.isBlank() ? phone.trim() : null);
        user.setCourse(course != null && !course.isBlank() ? course.trim() : null);
        user.setRollNumber(rollNumber != null && !rollNumber.isBlank() ? rollNumber.trim() : null);
        user.setActive(active);

        if (resetPassword != null && !resetPassword.isBlank()) {
            if (resetPassword.length() < 8) {
                throw new IllegalArgumentException("Reset password must be at least 8 characters");
            }
            user.setPassword(passwordEncoder.encode(resetPassword));
        }

        return userRepository.save(user);
    }

    // ---------------------------------------------------------------
    // 3. Services and stages
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<com.tiet.docket.model.Service> getAllServices() {
        return serviceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public com.tiet.docket.model.Service getServiceById(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found with id: " + id));
    }

    /**
     * Updates service description and active status.
     * Name and updatesStudentRecord cannot be changed as engine rules depend on them.
     */
    public com.tiet.docket.model.Service updateService(Long serviceId, String description, boolean active) {
        com.tiet.docket.model.Service service = getServiceById(serviceId);
        service.setDescription(description != null ? description.trim() : "");
        service.setActive(active);
        return serviceRepository.save(service);
    }

    /**
     * Adds a new service purely by configuration (QR18). It starts inactive so the
     * Administrator can add its stages and documents before students see it.
     */
    public com.tiet.docket.model.Service createService(String name, String description,
                                                       ApplicationStatus finalStatus, boolean requiresVisit) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (serviceRepository.findByName(name.trim()).isPresent()) {
            throw new IllegalArgumentException("A service named '" + name.trim() + "' already exists");
        }
        if (finalStatus != ApplicationStatus.APPROVED && finalStatus != ApplicationStatus.VERIFIED) {
            throw new IllegalArgumentException("Final outcome must be APPROVED or VERIFIED");
        }
        com.tiet.docket.model.Service service = new com.tiet.docket.model.Service();
        service.setName(name.trim());
        service.setDescription(description != null ? description.trim() : "");
        service.setFinalStatus(finalStatus);
        service.setRequiresVisit(requiresVisit);
        service.setActive(false);
        return serviceRepository.save(service);
    }

    /** Sets the online application fee in rupees; 0 means the service is free. */
    public com.tiet.docket.model.Service setServiceFee(Long serviceId, int feeAmount) {
        if (feeAmount < 0 || feeAmount > 100000) {
            throw new IllegalArgumentException("The fee must be between Rs. 0 and Rs. 1,00,000");
        }
        com.tiet.docket.model.Service service = getServiceById(serviceId);
        service.setFeeAmount(feeAmount);
        return serviceRepository.save(service);
    }

    /** Clears a login lock (QR16) so the user can sign in again straight away. */
    public User unlockUser(Long userId) {
        User user = getUserById(userId);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        return userRepository.save(user);
    }

    /** Marks a stage as used only when the previous officer forwards to it. */
    public Stage setStageOptional(Long stageId, boolean optional) {
        Stage stage = getStageById(stageId);
        stage.setOptional(optional);
        return stageRepository.save(stage);
    }

    @Transactional(readOnly = true)
    public List<Stage> getStagesForService(Long serviceId) {
        com.tiet.docket.model.Service service = getServiceById(serviceId);
        return stageRepository.findByServiceOrderByStepOrderAsc(service);
    }

    @Transactional(readOnly = true)
    public Stage getStageById(Long stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("Stage not found with id: " + stageId));
    }

    /**
     * Appends a new stage at the END of a service workflow (stepOrder = max + 1).
     * Existing stages are never reordered or deleted to preserve in-flight applications.
     */
    public Stage addStage(Long serviceId, String name, Role handlerRole, String department) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Stage name is required");
        }
        if (handlerRole == null) {
            throw new IllegalArgumentException("Handler role is required");
        }

        com.tiet.docket.model.Service service = getServiceById(serviceId);
        List<Stage> existingStages = stageRepository.findByServiceOrderByStepOrderAsc(service);
        int nextOrder = existingStages.isEmpty() ? 1 : existingStages.get(existingStages.size() - 1).getStepOrder() + 1;

        Stage stage = new Stage();
        stage.setService(service);
        stage.setName(name.trim());
        stage.setHandlerRole(handlerRole);
        stage.setDepartment(department != null && !department.isBlank() ? department.trim() : null);
        stage.setStepOrder(nextOrder);

        return stageRepository.save(stage);
    }

    /**
     * Edits an existing stage's name, handlerRole, and department.
     * Does not allow changing stepOrder or owning service.
     */
    public Stage updateStage(Long stageId, String name, Role handlerRole, String department) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Stage name is required");
        }
        if (handlerRole == null) {
            throw new IllegalArgumentException("Handler role is required");
        }

        Stage stage = getStageById(stageId);
        stage.setName(name.trim());
        stage.setHandlerRole(handlerRole);
        stage.setDepartment(department != null && !department.isBlank() ? department.trim() : null);

        return stageRepository.save(stage);
    }

    // ---------------------------------------------------------------
    // 4. Required documents
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RequiredDocument> getDocumentsForService(Long serviceId) {
        com.tiet.docket.model.Service service = getServiceById(serviceId);
        return requiredDocumentRepository.findByService(service);
    }

    /**
     * Adds a new required document for a service.
     * Rejects duplicate document names for the same service.
     */
    public RequiredDocument addRequiredDocument(Long serviceId, String documentName, boolean mandatory) {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("Document name is required");
        }

        com.tiet.docket.model.Service service = getServiceById(serviceId);
        String trimmedName = documentName.trim();
        if (requiredDocumentRepository.existsByServiceAndDocumentName(service, trimmedName)) {
            throw new IllegalArgumentException("A document named '" + trimmedName + "' already exists for this service");
        }

        RequiredDocument doc = new RequiredDocument();
        doc.setService(service);
        doc.setDocumentName(trimmedName);
        doc.setMandatory(mandatory);

        return requiredDocumentRepository.save(doc);
    }

    /**
     * Toggles whether an existing document is mandatory.
     */
    public RequiredDocument toggleRequiredDocumentMandatory(Long documentId) {
        RequiredDocument doc = requiredDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));
        doc.setMandatory(!doc.isMandatory());
        return requiredDocumentRepository.save(doc);
    }
}
