package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {
    Optional<FeePayment> findByApplication(Application application);
    Optional<FeePayment> findByOrderId(String orderId);
    boolean existsByPaymentId(String paymentId);
    boolean existsByApplicationAndVerification(Application application, FeePayment.Verification verification);

    // Finance cross-check list: every completed payment, newest first
    List<FeePayment> findByStatusOrderByPaidAtDesc(FeePayment.Status status);
}
