package com.jovens.yoga.repository;

import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByStripeEventId(String stripeEventId);
    Optional<Payment> findByStripeEventId(String stripeEventId);
    Optional<Payment> findFirstByStripePaymentIntentIdOrderByCreatedAtDesc(String stripePaymentIntentId);
    Optional<Payment> findFirstByTrialIdAndStatusOrderByPaidAtDesc(Long trialId, PaymentStatus status);
    long countByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE (:status IS NULL OR p.status = :status)")
    Page<Payment> search(@Param("status") PaymentStatus status, Pageable pageable);
}
