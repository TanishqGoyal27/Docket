package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // Student dashboard: all of one student's applications, newest first
    List<Application> findByStudentOrderBySubmittedAtDesc(User student);

    // Staff queue: applications waiting at a stage handled by this role
    List<Application> findByCurrentStage_HandlerRoleAndStatusIn(Role role, List<ApplicationStatus> statuses);

    Optional<Application> findByReferenceNumber(String referenceNumber);
}