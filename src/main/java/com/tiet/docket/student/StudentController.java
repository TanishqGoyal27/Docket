package com.tiet.docket.student;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudentController {

    @GetMapping("/student/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("email", authentication.getName());
        return "student/dashboard";
    }
}