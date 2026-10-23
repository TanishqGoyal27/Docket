package com.tiet.docket.staff;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffController {

    @GetMapping("/staff/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pendingCount", 5);
        return "staff-dashboard";
    }
}
