package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionHistoryRepository extends JpaRepository<DecisionHistory, Long> {

    // Timeline on the application detail and review pages, oldest first
    List<DecisionHistory> findByApplicationOrderByDecidedAtAsc(Application application);

    // Staff "my history": every decision this officer took, newest first
    List<DecisionHistory> findByOfficerOrderByDecidedAtDesc(User officer);
}