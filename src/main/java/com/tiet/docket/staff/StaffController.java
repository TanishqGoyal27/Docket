package com.tiet.docket.staff;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffController {

    @GetMapping("/staff/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pendingCount", 3);
        return "staff-dashboard";
    }

    @GetMapping("/staff/queue")
    public String pendingQueue(Model model) {

        // TODO: Replace sample data with WorkflowService data.
        List<Map<String, String>> applications = List.of(
                Map.of(
                        "id", "APP-001",
                        "student", "Rahul Sharma",
                        "type", "Bonafide Certificate",
                        "date", "23 Oct 2026",
                        "status", "Pending"),
                Map.of(
                        "id", "APP-002",
                        "student", "Priya Verma",
                        "type", "Fee Concession",
                        "date", "22 Oct 2026",
                        "status", "Pending"),
                Map.of(
                        "id", "APP-003",
                        "student", "Aman Singh",
                        "type", "Hostel Application",
                        "date", "21 Oct 2026",
                        "status", "Pending"));

        model.addAttribute("applications", applications);
        return "staff-queue";
    }

    @GetMapping("/staff/review")
    public String reviewApplication(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "APP-001") String id,
            Model model) {

        List<Map<String, String>> applications = List.of(
                Map.of(
                        "id", "APP-001",
                        "student", "Rahul Sharma",
                        "type", "Bonafide Certificate",
                        "date", "23 Oct 2026",
                        "status", "Pending"),
                Map.of(
                        "id", "APP-002",
                        "student", "Priya Verma",
                        "type", "Fee Concession",
                        "date", "22 Oct 2026",
                        "status", "Pending"),
                Map.of(
                        "id", "APP-003",
                        "student", "Aman Singh",
                        "type", "Hostel Application",
                        "date", "21 Oct 2026",
                        "status", "Pending"));

        Map<String, String> selectedApplication = applications.stream()
                .filter(app -> app.get("id").equals(id))
                .findFirst()
                .orElse(applications.get(0));

        model.addAttribute("applicationId", selectedApplication.get("id"));
        model.addAttribute("studentName", selectedApplication.get("student"));
        model.addAttribute("applicationType", selectedApplication.get("type"));
        model.addAttribute("submittedOn", selectedApplication.get("date"));
        model.addAttribute("status", selectedApplication.get("status"));

        return "staff-review";
    }
}
