package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByApplication(Application application);
    boolean existsBySlot(LocalDateTime slot);
    List<Appointment> findBySlotAfterOrderBySlotAsc(LocalDateTime from);
}
