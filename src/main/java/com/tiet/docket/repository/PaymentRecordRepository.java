package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Database operations for PaymentRecord.
 *
 * findByApplication  - retrieve the payment record (if any) for an application.
 * existsByApplication - quickly check if one was already saved (used in approve() guard).
 */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByApplication(Application application);

    boolean existsByApplication(Application application);
}
