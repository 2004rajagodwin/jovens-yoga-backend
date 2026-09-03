package com.jovens.yoga.repository;

import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.enums.TrialStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrialRepository extends JpaRepository<Trial, Long> {
    Optional<Trial> findByUserId(Long userId);
    Optional<Trial> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<Trial> findByAccessTokenHashAndAccessTokenExpiresAtAfter(String accessTokenHash, LocalDateTime now);
    List<Trial> findByStatusAndTrialExpiryDateBefore(TrialStatus status, LocalDateTime dateTime);
    List<Trial> findByStatus(TrialStatus status);
    long countByStatus(TrialStatus status);

    @Query("SELECT t FROM Trial t WHERE (:status IS NULL OR t.status = :status) "
            + "AND (:search IS NULL OR LOWER(t.user.email) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(t.user.firstName) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(t.user.lastName) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(t.user.mobileNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Trial> search(@Param("status") TrialStatus status, @Param("search") String search, Pageable pageable);
}
