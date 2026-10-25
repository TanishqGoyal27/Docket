package com.tiet.docket.student;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.tiet.docket.model.Notification;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.NotificationService;

@Controller
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // Displays the student's notifications with unread ones highlighted
    @GetMapping("/student/notifications")
    public String listNotifications(Authentication authentication, Model model) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        List<Notification> notifications = notificationService.getForUser(student);
        long unreadCount = notificationService.countUnread(student);

        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("studentName", student.getName());

        return "student/notifications";
    }

    // Marks a single notification as read
    @PostMapping("/student/notifications/{id}/read")
    public String markRead(@PathVariable Long id, Authentication authentication) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        notificationService.markRead(id, student);
        return "redirect:/student/notifications";
    }

    // Marks all notifications for the current student as read
    @PostMapping("/student/notifications/read-all")
    public String markAllRead(Authentication authentication) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not found"));

        notificationService.markAllRead(student);
        return "redirect:/student/notifications";
    }
}
