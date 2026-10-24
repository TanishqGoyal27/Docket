package com.tiet.docket.repository;

import com.tiet.docket.model.Notification;
import com.tiet.docket.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Notification list page, newest first
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    // Number shown on the bell
    long countByUserAndReadFalse(User user);

    List<Notification> findByUserAndReadFalse(User user);
}