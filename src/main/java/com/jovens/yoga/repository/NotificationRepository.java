package com.jovens.yoga.repository;

import com.jovens.yoga.entity.Notification;
import com.jovens.yoga.enums.NotificationChannel;
import com.jovens.yoga.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByEventKey(String eventKey);

    @Query("SELECT n FROM Notification n WHERE (:status IS NULL OR n.status = :status) "
            + "AND (:channel IS NULL OR n.channel = :channel)")
    Page<Notification> search(@Param("status") NotificationStatus status,
                               @Param("channel") NotificationChannel channel,
                               Pageable pageable);
}
