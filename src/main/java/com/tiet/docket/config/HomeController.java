package com.tiet.docket.config;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // After login, Spring sends everyone to "/".
    // Here we send each role to its own area.
    @GetMapping("/")
    public String home(Authentication authentication) {

        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        boolean isAdministrator = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));

        if (isStudent) {
            return "redirect:/student/dashboard";
        }
        if (isAdministrator) {
            return "redirect:/admin/home";
        }
        return "redirect:/staff/home";
    }
}