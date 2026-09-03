package com.jovens.yoga.repository;

import com.jovens.yoga.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByActiveTrueOrderByDisplayOrderAsc();
    List<Plan> findAllByOrderByDisplayOrderAsc();
}
