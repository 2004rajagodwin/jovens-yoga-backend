package com.jovens.yoga.repository;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
    Optional<CustomerOrder> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    Optional<CustomerOrder> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);
    List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByStatus(OrderStatus status);

    @Query("SELECT o FROM CustomerOrder o WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:search IS NULL OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(o.user.email) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(o.user.firstName) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(o.user.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CustomerOrder> search(@Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);
}
