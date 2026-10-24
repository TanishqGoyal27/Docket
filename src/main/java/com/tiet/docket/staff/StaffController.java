package com.tiet.docket.staff;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffController {

        // ---------- Portal (role chooser) ----------
        @GetMapping("/")
        public String home() {
                return "redirect:/staff/portal";
        }

        @GetMapping("/staff/portal")
        public String portal(Model model) {
                model.addAttribute("officerRoles", List.of(
                                new StaffRoleCard("ADMIN_OFFICER", "Administrative Officer",
                                                "Verifies ID card applications, reviews correction requests and updates student records.",
                                                "🗂️", "approval"),
                                new StaffRoleCard("PROFESSOR", "Professor / Supervisor",
                                                "Verifies project relevance and expenditure of reimbursement claims.",
                                                "🎓", "approval"),
                                new StaffRoleCard("HOD", "Head of Department",
                                                "Approves or rejects verified reimbursement claims.", "🏛️",
                                                "approval"),
                                new StaffRoleCard("SCHOLARSHIP_OFFICER", "Scholarship Officer",
                                                "Verifies scholarship documents and eligibility.", "🏅", "approval"),
                                new StaffRoleCard("FINANCE_OFFICER", "Finance Officer",
                                                "Verifies ID card fees and reimbursement bills.", "💳", "finance"),
                                new StaffRoleCard("ACCOUNTS_OFFICER", "Accounts Officer",
                                                "Records payment reference and date, then marks claims as processed.",
                                                "🧾", "finance")));
                model.addAttribute("adminRole", new StaffRoleCard("ADMINISTRATOR", "Administrator",
                                "Manages users, roles, departments, required documents and approval sequences.", "⚙️",
                                "admin"));
                return "staff-portal";
        }

        // ---------- Dashboard ----------

        @GetMapping("/staff/dashboard")
        public String dashboard(Model model) {
                // TODO: replace with workflowService.getPendingFor(officer).size()
                model.addAttribute("pendingCount", sampleApplications().size());
                return "staff-dashboard";
        }

        // ---------- Pending queue ----------

        @GetMapping("/staff/queue")
        public String pendingQueue(Model model) {
                // TODO: replace with workflowService.getPendingFor(officer)
                model.addAttribute("applications", sampleApplications());
                return "staff-queue";
        }

        // ---------- Review page ----------

        @GetMapping("/staff/review")
        public String reviewApplication(@RequestParam String id, Model model) {
                // TODO: load the real Application (must check it belongs to this officer's
                // stage)
                Map<String, String> app = sampleApplications().stream()
                                .filter(a -> a.get("id").equals(id))
                                .findFirst()
                                .orElse(null);

                if (app == null) {
                        return "redirect:/staff/queue";
                }

                model.addAttribute("applicationId", app.get("id"));
                model.addAttribute("studentName", app.get("student"));
                model.addAttribute("applicationType", app.get("type"));
                model.addAttribute("submittedOn", app.get("date"));
                model.addAttribute("status", app.get("status"));

                // TODO: replace with real DecisionHistory rows for this application
                model.addAttribute("history", List.of(
                                Map.of("date", "23 Oct 2026, 10:15", "officer", "System",
                                                "decision", "SUBMITTED", "reason",
                                                "Application submitted by student")));

                return "staff-review";
        }

        // ---------- Decision (Approve / Reject / Request Correction) ----------

        @PostMapping("/staff/review/decision")
        public String submitDecision(@RequestParam String id,
                        @RequestParam String action,
                        @RequestParam(defaultValue = "") String reason,
                        RedirectAttributes redirect) {

                String text = reason.trim();
                boolean validAction = action.equals("approve") || action.equals("reject")
                                || action.equals("correction");
                boolean needsReason = action.equals("reject") || action.equals("correction");

                if (!validAction) {
                        redirect.addFlashAttribute("error", "Unknown action.");
                        return "redirect:/staff/queue";
                }

                // Server-side rule: rejection reason / correction message is mandatory
                if (needsReason && text.isEmpty()) {
                        redirect.addFlashAttribute("error", action.equals("reject")
                                        ? "A reason is required to reject an application."
                                        : "Please describe what the student needs to correct.");
                        redirect.addAttribute("id", id);
                        return "redirect:/staff/review";
                }

                // TODO: call the real WorkflowService with the logged-in officer:
                // approve -> workflowService.approve(applicationId, officer)
                // reject -> workflowService.reject(applicationId, officer, text)
                // correction -> workflowService.requestCorrection(applicationId, officer, text)
                redirect.addFlashAttribute("success",
                                "Demo only: '" + action + "' for " + id + " was not saved yet.");
                return "redirect:/staff/queue";
        }

        // ---------- Sample data (remove when WorkflowService is merged) ----------

        // TODO: delete once WorkflowService.getPendingFor() is merged
        private List<Map<String, String>> sampleApplications() {
                return List.of(
                                Map.of("id", "APP-001", "student", "Rahul Sharma", "type", "ID Card",
                                                "date", "23 Oct 2026", "status", "SUBMITTED"),
                                Map.of("id", "APP-002", "student", "Priya Verma", "type", "Project Reimbursement",
                                                "date", "22 Oct 2026", "status", "UNDER_REVIEW"),
                                Map.of("id", "APP-003", "student", "Aman Singh", "type", "Details Correction",
                                                "date", "21 Oct 2026", "status", "SUBMITTED"));
        }
}