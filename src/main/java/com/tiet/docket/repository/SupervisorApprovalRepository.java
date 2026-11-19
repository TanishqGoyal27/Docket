package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.SupervisorApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupervisorApprovalRepository extends JpaRepository<SupervisorApproval, Long> {
    Optional<SupervisorApproval> findByApplication(Application application);
    Optional<SupervisorApproval> findByTokenHash(String tokenHash);
    boolean existsByApplicationAndStatus(Application application, SupervisorApproval.Status status);
}
