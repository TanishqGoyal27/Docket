package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.DecisionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionHistoryRepository extends JpaRepository<DecisionHistory, Long> {

    // Timeline on the application detail and review pages, oldest first
    List<DecisionHistory> findByApplicationOrderByDecidedAtAsc(Application application);
}