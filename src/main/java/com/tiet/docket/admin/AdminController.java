package com.tiet.docket.admin;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Administrator Console under /admin/**.
 * Enforces ADMINISTRATOR role on every endpoint and returns HTTP 403 otherwise.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    public AdminController(AdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    /**
     * Helper to verify that the logged-in user is an active ADMINISTRATOR.
     * Throws HTTP 403 Forbidden if not authenticated or not an Administrator.
     */
    private User requireAdministrator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User account not found"));
        if (user.getRole() != Role.ADMINISTRATOR || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Administrator role required");
        }
        return user;
    }

    // ---------------------------------------------------------------
    // 1. Dashboard
    // ---------------------------------------------------------------

    @GetMapping({"", "/", "/home"})
    public String dashboard(Authentication authentication, Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);
        model.addAttribute("userCount", adminService.getUserCount());
        model.addAttribute("serviceCount", adminService.getServiceCount());
        model.addAttribute("stageCount", adminService.getStageCount());
        return "admin/dashboard";
    }

    // ---------------------------------------------------------------
    // 2. Users
    // ---------------------------------------------------------------

    @GetMapping("/users")
    public String listUsers(Authentication authentication, Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);
        model.addAttribute("users", adminService.getAllUsers());
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Authentication authentication, Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);
        model.addAttribute("roles", Role.values());
        return "admin/user-create";
    }

    @PostMapping("/users")
    public String createUser(Authentication authentication,
                             @RequestParam String name,
                             @RequestParam String email,
                             @RequestParam String password,
                             @RequestParam Role role,
                             @RequestParam(required = false) String department,
                             @RequestParam(required = false) String rollNumber,
                             @RequestParam(required = false) String course,
                             @RequestParam(required = false) String phone,
                             RedirectAttributes redirectAttributes) {
        requireAdministrator(authentication);
        try {
            adminService.createUser(name, email, password, role, department, rollNumber, course, phone);
            redirectAttributes.addFlashAttribute("success", "User created successfully: " + email);
            return "redirect:/admin/users";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/users/new";
        }
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(Authentication authentication, @PathVariable Long id, Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);
        model.addAttribute("user", adminService.getUserById(id));
        model.addAttribute("roles", Role.values());
        return "admin/user-edit";
    }

    @PostMapping("/users/{id}/edit")
    public String editUser(Authentication authentication,
                           @PathVariable Long id,
                           @RequestParam String name,
                           @RequestParam Role role,
                           @RequestParam(required = false) String department,
                           @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String course,
                           @RequestParam(required = false) String rollNumber,
                           @RequestParam(defaultValue = "false") boolean active,
                           @RequestParam(required = false) String resetPassword,
                           RedirectAttributes redirectAttributes) {
        User admin = requireAdministrator(authentication);
        try {
            adminService.updateUser(id, admin.getId(), name, role, department, phone, course, rollNumber, active, resetPassword);
            redirectAttributes.addFlashAttribute("success", "User updated successfully.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/users/" + id + "/edit";
        }
    }

    // ---------------------------------------------------------------
    // 3. Services and Stages
    // ---------------------------------------------------------------

    @GetMapping("/services")
    public String listServices(Authentication authentication, Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);

        List<com.tiet.docket.model.Service> services = adminService.getAllServices();
        Map<Long, Integer> stageCounts = new HashMap<>();
        Map<Long, Integer> docCounts = new HashMap<>();
        for (com.tiet.docket.model.Service s : services) {
            stageCounts.put(s.getId(), adminService.getStagesForService(s.getId()).size());
            docCounts.put(s.getId(), adminService.getDocumentsForService(s.getId()).size());
        }

        model.addAttribute("services", services);
        model.addAttribute("stageCounts", stageCounts);
        model.addAttribute("docCounts", docCounts);
        return "admin/services";
    }

    @PostMapping("/services/{id}/edit")
    public String updateService(Authentication authentication,
                                @PathVariable Long id,
                                @RequestParam String description,
                                @RequestParam(defaultValue = "false") boolean active,
                                RedirectAttributes redirectAttributes) {
        requireAdministrator(authentication);
        try {
            adminService.updateService(id, description, active);
            redirectAttributes.addFlashAttribute("success", "Service updated successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services";
    }

    @GetMapping("/services/{id}")
    public String serviceDetail(Authentication authentication, @PathVariable Long id, Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);

        com.tiet.docket.model.Service service = adminService.getServiceById(id);
        List<Stage> stages = adminService.getStagesForService(id);
        List<RequiredDocument> documents = adminService.getDocumentsForService(id);

        model.addAttribute("service", service);
        model.addAttribute("stages", stages);
        model.addAttribute("documents", documents);
        model.addAttribute("roles", Role.values());
        return "admin/service-detail";
    }

    @PostMapping("/services/{id}/stages")
    public String addStage(Authentication authentication,
                           @PathVariable Long id,
                           @RequestParam String name,
                           @RequestParam Role handlerRole,
                           @RequestParam(required = false) String department,
                           RedirectAttributes redirectAttributes) {
        requireAdministrator(authentication);
        try {
            adminService.addStage(id, name, handlerRole, department);
            redirectAttributes.addFlashAttribute("success", "Stage appended successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services/" + id;
    }

    @GetMapping("/services/{serviceId}/stages/{stageId}/edit")
    public String editStageForm(Authentication authentication,
                                @PathVariable Long serviceId,
                                @PathVariable Long stageId,
                                Model model) {
        User admin = requireAdministrator(authentication);
        model.addAttribute("currentAdmin", admin);
        model.addAttribute("service", adminService.getServiceById(serviceId));
        model.addAttribute("stage", adminService.getStageById(stageId));
        model.addAttribute("roles", Role.values());
        return "admin/stage-edit";
    }

    @PostMapping("/services/{serviceId}/stages/{stageId}/edit")
    public String editStage(Authentication authentication,
                            @PathVariable Long serviceId,
                            @PathVariable Long stageId,
                            @RequestParam String name,
                            @RequestParam Role handlerRole,
                            @RequestParam(required = false) String department,
                            RedirectAttributes redirectAttributes) {
        requireAdministrator(authentication);
        try {
            adminService.updateStage(stageId, name, handlerRole, department);
            redirectAttributes.addFlashAttribute("success", "Stage updated successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services/" + serviceId;
    }

    // ---------------------------------------------------------------
    // 4. Required Documents
    // ---------------------------------------------------------------

    @PostMapping("/services/{id}/documents")
    public String addDocument(Authentication authentication,
                              @PathVariable Long id,
                              @RequestParam String documentName,
                              @RequestParam(defaultValue = "false") boolean mandatory,
                              RedirectAttributes redirectAttributes) {
        requireAdministrator(authentication);
        try {
            adminService.addRequiredDocument(id, documentName, mandatory);
            redirectAttributes.addFlashAttribute("success", "Required document added successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services/" + id;
    }

    @PostMapping("/services/{serviceId}/documents/{docId}/toggle")
    public String toggleDocument(Authentication authentication,
                                 @PathVariable Long serviceId,
                                 @PathVariable Long docId,
                                 RedirectAttributes redirectAttributes) {
        requireAdministrator(authentication);
        try {
            adminService.toggleRequiredDocumentMandatory(docId);
            redirectAttributes.addFlashAttribute("success", "Document requirement updated.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services/" + serviceId;
    }
}
