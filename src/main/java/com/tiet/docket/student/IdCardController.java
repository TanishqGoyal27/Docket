package com.tiet.docket.student;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;

@Controller
public class IdCardController {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;

    public IdCardController(UserRepository userRepository,
                            ServiceRepository serviceRepository,
                            RequiredDocumentRepository requiredDocumentRepository) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    // Displays the ID Card application form with prefilled student details and document checklist
    @GetMapping("/student/apply/id-card")
    public String showForm(Authentication authentication, Model model) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        Service service = serviceRepository.findByName("ID Card")
                .orElseThrow(() -> new IllegalStateException("ID Card service is not configured"));

        List<RequiredDocument> documents = requiredDocumentRepository.findByService(service);

        model.addAttribute("student", student);
        model.addAttribute("documents", documents);

        return "student/id-card-form";
    }
}
