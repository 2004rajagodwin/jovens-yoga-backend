package com.jovens.yoga.repository;

import com.jovens.yoga.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {
    List<PlanFeature> findByPlanIdOrderByDisplayOrderAsc(Long planId);
}
