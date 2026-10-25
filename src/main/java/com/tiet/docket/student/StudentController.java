package com.tiet.docket.student;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.UserRepository;

@Controller
public class StudentController {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    public StudentController(UserRepository userRepository,
            ApplicationRepository applicationRepository) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/student/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        // The login name is the email, so we find the student from it.
        // Only this student's own applications are loaded.
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        List<Application> applications = applicationRepository.findByStudentOrderBySubmittedAtDesc(student);

        model.addAttribute("studentName", student.getName());
        model.addAttribute("applications", applications);
        return "student/dashboard";
    }
}