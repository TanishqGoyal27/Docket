package com.tiet.docket.repository;

import com.tiet.docket.model.Service;
import com.tiet.docket.model.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, Long> {

    List<Stage> findByServiceOrderByStepOrderAsc(Service service);

    Optional<Stage> findByServiceAndStepOrder(Service service, int stepOrder);
}